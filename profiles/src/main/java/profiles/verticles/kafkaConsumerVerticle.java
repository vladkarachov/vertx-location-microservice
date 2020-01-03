package profiles.verticles;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import profiles.model.LocationData;
import profiles.model.LocationDataCodec;
import profiles.model.*;
import vertx.common.MicroserviceVerticle;
import io.vertx.core.json.JsonObject;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static profiles.verticles.GoogleAPI.GET_METADATA;
import static profiles.verticles.LocationVerticle.*;
import static profiles.verticles.kafkaProducerVerticle.*;

public class kafkaConsumerVerticle extends MicroserviceVerticle {
    public static final String KAFKA_ADRESS = "localhost:9092";

    public static final String KAFKA_GET_TOPIC = "TimelapseGetGeo";
    public static final String KAFKA_PUT_TOPIC = "TimelapsePutGeo";
    public static final String KAFKA_DELETE_TOPIC = "DeleteTimelapse";
    Mapper mapper = new Mapper();

    @Override
    public void start(Promise<Void> startPromise) {
        createServiceDiscovery();
        registerCodecs();
        setupConsumer();
    }

    private void registerCodecs() {
        try {
            vertx.eventBus().registerDefaultCodec(LocationData.class, new LocationDataCodec());
        } catch (IllegalStateException ignored) {}
    }
    private void setupConsumer() {
        Map<String, String> config = new HashMap<>();
        config.put("bootstrap.servers", KAFKA_ADRESS);
        config.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        config.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        config.put("group.id", "my_group");
        config.put("enable.auto.commit", "false");

        // use consumer for interacting with Apache Kafka
        KafkaConsumer<String, String> consumer = KafkaConsumer.create(vertx, config);
        consumer.handler(record -> {
                    if (record.topic().equals(KAFKA_GET_TOPIC)) getLocation(record);
                    if (record.topic().equals(KAFKA_PUT_TOPIC)) putLocation(record);
                    if(record.topic().equals(KAFKA_DELETE_TOPIC)) deleteLocation(record);
                });



        // subscribe to several topics with list
        Set<String> topics = new HashSet<>();
        topics.add(KAFKA_GET_TOPIC);
        topics.add(KAFKA_PUT_TOPIC);
        topics.add(KAFKA_DELETE_TOPIC);
        consumer.subscribe(topics);

    }

    private void putStatus(JsonObject response) {
        vertx.eventBus().request(KAFKA_PUT_STATUS, response, handl -> {});
    }

    private void getLocation(@Nonnull KafkaConsumerRecord<String, String> record) {
        Buffer buf = Buffer.buffer();
        //надо как-то получить ид и не сломать апи вертикл поетому эта строка такой кошмар
        try {
            //buf.appendString(new JsonObject(record.value()).getString("id"));
            buf.appendString(record.key());
        } catch (Exception e) {
            JsonObject response = mapper.FalueResponce(record, e.toString());
            putStatus(response);
            return;
        }
        getLocationFromDb(record, buf);
    }

    private void putLocation(@Nonnull KafkaConsumerRecord<String, String> record) {
        LocationData loc=null;
        try {
            //loc = new LocationData(new JsonObject(record.value()));
            //loc.setId(record.key());
            loc=mapper.recordToLoc(record);
        } catch (Exception e) {
            JsonObject response = mapper.FalueResponce(record, e.getCause().toString());
            putStatus(response);
            return;
        }
        getMetadata(loc, record).setHandler(asyncRes->{
            if(asyncRes.succeeded()){
                writeLocationToDb(asyncRes.result(), record);
            }
            else {
                JsonObject response = mapper.FalueResponce(record, asyncRes.cause().toString());
                putStatus(response);
            }
        });
    }

    private void deleteLocation(@Nonnull KafkaConsumerRecord<String, String> record){
        Buffer buf = Buffer.buffer();
        try {
            //buf.appendString(new JsonObject(record.value()).getString("id"));
            buf.appendString(record.key());
        }
        catch (Exception e) {
            String errDescr = "Decoding error - you must provide id of timelapse";
            JsonObject response = mapper.FalueResponce(record, errDescr);
            putStatus(response);
            return;
        }
        vertx.eventBus().request(DELETE_LOCATION, buf, ar->{
            if(ar.failed()){
                JsonObject response = mapper.FalueResponce(record, ar.cause().toString());
                putStatus(response);
                return;
            }
            JsonObject response = mapper.SuccessResponce(record);
            putStatus(response);
            return;
        });
    }

    private Future<LocationData> getMetadata(LocationData loc, KafkaConsumerRecord<String, String> record){
        Future<LocationData> metaFut = Future.future();
        vertx.eventBus().request(GET_METADATA, loc, ar-> {
            if (ar.failed()) {
                JsonObject response = mapper.FalueResponce(record, ar.cause().toString());
                putStatus(response);
                metaFut.fail(ar.cause());
            }
            else {
                metaFut.complete((LocationData) ar.result().body());
            }
        });
        return metaFut;
    }

    private void writeLocationToDb(LocationData loc, KafkaConsumerRecord<String, String> record){
        vertx.eventBus().request(PUT_LOCATION, loc, handl -> {
            if (handl.failed()) {
                JsonObject response = mapper.FalueResponce(record, handl.cause().toString());
                putStatus(response);
                return;
            }
            //JsonObject elSearch = mapper.mapElasticSearchKafka(record, loc);
            putToElasticSearch(loc.toJson(), record);
        });

    }

    private void getLocationFromDb(KafkaConsumerRecord<String, String> record, Buffer buf) {
        vertx.eventBus().request(GET_LOCATION, buf, ar -> {
            if (ar.failed()) {
                JsonObject response = mapper.FalueResponce(record, ar.cause().toString());
                putStatus(response);
                return;
            }
            LocationData loc = (LocationData) ar.result().body();
            putLocationToKafka(loc, record);
        });
    }

    private void putLocationToKafka(LocationData loc, KafkaConsumerRecord<String, String> record){
        vertx.eventBus().request(KAFKA_PUT_LOCATION, loc.toJson(), handl -> {
            if (handl.failed()) {
                System.out.println(handl.cause());
                JsonObject response = mapper.FalueResponce(record, handl.cause().toString());
                putStatus(response);
                return;
            }
            JsonObject response = mapper.SuccessResponce(record);
            putStatus(response);
        });
    }
    private void putToElasticSearch(JsonObject elSearch, KafkaConsumerRecord<String, String> record) {
        vertx.eventBus().request(KAFKA_PUT_SEARCH, elSearch, ARSearch -> {
            if (ARSearch.failed()) {
                JsonObject response = mapper.FalueResponce(record, ARSearch.cause().toString());
                putStatus(response);
                return;
            }
            JsonObject response = mapper.SuccessResponce(record);
            putStatus(response);
        });
    }
}


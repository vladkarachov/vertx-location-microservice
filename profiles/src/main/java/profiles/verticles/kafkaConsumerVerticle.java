package profiles.verticles;

import Location.LocationObject;
import Location.resp;
import io.grpc.StatusException;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.FailedFuture;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;
import io.vertx.kafka.client.producer.KafkaProducer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import profiles.model.LocationData;
import profiles.model.LocationDataCodec;
import profiles.model.*;
import vertx.common.MicroserviceVerticle;
import io.vertx.config.ConfigChange;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.json.JsonObject;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static profiles.verticles.GoogleAPI.GET_METADATA;
import static profiles.verticles.LocationVerticle.*;
import static profiles.verticles.kafkaProducerVerticle.*;

public class kafkaConsumerVerticle extends MicroserviceVerticle {
    public static final String KAFKA_ADRESS = "localhost:9092";

    public static final String KAFKA_GET_TOPIC = "TimelapseGetGeo";
    public static final String KAFKA_PUT_TOPIC = "TimelapsePutGeo";
    public static final String KAFKA_DELETE_TOPIC = "DeleteTimelapse";


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
                    //get location from db   ----------------------------------- заменить на вызов
                    if (record.topic().equals(KAFKA_GET_TOPIC)) getLocation(record);

                    //put location to db and so on ----------------------------------
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
        vertx.eventBus().request(KAFKA_PUT_STATUS, response, handl -> {
            if (handl.failed()) {
                System.out.println(handl.cause());
                return;
            }
        });
    }

    private void getLocation(@Nonnull KafkaConsumerRecord<String, String> record) {
        Buffer buf = Buffer.buffer();
        //надо как-то получить ид и не сломать апи вертикл поетому эта строка такой кошмар
        try {
            buf.appendString(new JsonObject(record.value()).getString("id"));
        }
        catch (Exception e){
            System.out.println(e.getCause());
            JsonObject response = new JsonObject().put("status","ERROR").put("message:", e.toString()).put("key", record.key());
            putStatus(response);
            return;
        }
        vertx.eventBus().request(GET_LOCATION, buf, ar -> {
            if (ar.failed()) {
                JsonObject response = new JsonObject()
                        .put("status","ERROR")
                        .put("message", ar.cause().toString())
                        .put("key", record.key());
                putStatus(response);
                return;
            }
            LocationData loc = (LocationData) ar.result().body();
            JsonObject res = loc.toJson().put("key", record.key());
            //отправить в кафку назад жсон локации
            vertx.eventBus().request(KAFKA_PUT_LOCATION, res, handl -> {
                if (handl.failed()) {
                    System.out.println(handl.cause());
                    JsonObject response = new JsonObject().put("status","ERROR").put("message:", handl.cause().toString()).put("key", record.key());
                    putStatus(response);
                    return;
                }
                JsonObject response = new JsonObject().put("status", "OK").put("key", record.key());
                putStatus(response);
            });

        });
    }

    private void putLocation(@Nonnull KafkaConsumerRecord<String, String> record){
        LocationData loc=null;
        try {
            loc = new LocationData(new JsonObject(record.value()));
        } catch (Exception e) {
            //todo ну что-то еще с этим сделать
            System.out.println(e.getCause().toString());
            return;
        }
        vertx.eventBus().request(GET_METADATA, loc, ar -> {
            if (ar.failed()) {
                JsonObject response = new JsonObject().put("status","ERROR").put("message:", ar.cause().toString()).put("key", record.key());
                putStatus(response);
                return;
            }
            //TODO
            LocationData loc2 = (LocationData) ar.result().body();
            System.out.println(loc2.toString());
            //записываем локацию в базу данных
            vertx.eventBus().request(PUT_LOCATION, loc2, handl -> {
                if (handl.failed()) {
                    JsonObject response = new JsonObject().put("status","ERROR").put("message", handl.cause().toString()).put("key", record.key());
                    putStatus(response);
                    return;
                }
                JsonObject elSearch=new JsonObject().put("id", loc2.getId()).put("city", loc2.getCity()).put("key", record.key());
                vertx.eventBus().request(KAFKA_PUT_SEARCH, elSearch, ARSearch -> {
                    if (ARSearch.failed()) {
                        System.out.println(ARSearch.cause().toString());
                        return;
                    }
                    JsonObject response = new JsonObject().put("status", "OK").put("key", record.key());
                    putStatus(response);
                    return;
                });
            });
        });
    }

    private void deleteLocation(@Nonnull KafkaConsumerRecord<String, String> record){
        Buffer buf = Buffer.buffer();
        try {
            buf.appendString(new JsonObject(record.value()).getString("id"));
        }
        catch (Exception e) {
            System.out.println(e.getCause());
            JsonObject response = new JsonObject().put("status", "ERROR").put("message:", "Decoding error - you must provide id of timelapse").put("key", record.key());
            putStatus(response);
            return;
        }
        vertx.eventBus().request(DELETE_LOCATION, buf, ar->{
            if(ar.failed()){
                JsonObject response = new JsonObject().put("status","ERROR").put("message", ar.cause().toString()).put("key", record.key());
                putStatus(response);
                return;
            }
            JsonObject response = new JsonObject().put("status", "OK").put("key", record.key());
            putStatus(response);
            return;
        });
    }
}


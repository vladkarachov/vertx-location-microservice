package profiles.verticles;

import Location.LocationServiceGrpc;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.Promise;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import profiles.model.LocationData;
import profiles.model.LocationDataCodec;
import profiles.model.Mapper;
import vertx.common.MicroserviceVerticle;

import java.util.HashMap;
import java.util.Map;

public class kafkaProducerVerticle extends MicroserviceVerticle {
    public static final String KAFKA_ADDRESS = "localhost:9092";
    //eventbus
    public static final String KAFKA_PUT_STATUS = "PUT_STATUS";
    public static final String KAFKA_PUT_LOCATION = "PUT_LOCATION_TO_KAFKA";
    public static final String KAFKA_PUT_SEARCH = "PUT_LOCATION_TO_KAFKA_FOR_SEARCH";
    //kafka
    public static final String STATUS_TOPIC = "LocStatus";
    public static final String RESP_LOC = "LocResp";
    public static final String SEARCH_PUT = "SearchPut";
    Mapper mapper = new Mapper();

    @Override
    public void start(Promise<Void> startPromise) {
        createServiceDiscovery();
        registerCodecs();
        setupProducer();
    }

    private void registerCodecs() {
        try {
            vertx.eventBus().registerDefaultCodec(LocationData.class, new LocationDataCodec());
        } catch (IllegalStateException ignored) {
        }
    }

    private void setupProducer() {
        Map<String, String> configProd = new HashMap<>();
        configProd.put("bootstrap.servers", KAFKA_ADDRESS);
        configProd.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        //по-идее, я должен отправлять джсон, но в кафку еще не добавили такого сериалайзера. Когда-то следущую строку надо будет обновить!
        configProd.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        configProd.put("acks", "1");

        // use producer for interacting with Apache Kafka
        KafkaProducer<String, String> producer = KafkaProducer.create(vertx, configProd);

        vertx.eventBus().<JsonObject>consumer(KAFKA_PUT_STATUS, handler -> {
            try {
                JsonObject response = mapper.mapResponce(handler.body());
                KafkaProducerRecord<String, String> record =
                        KafkaProducerRecord.create(STATUS_TOPIC, handler.body().getString("key"), response.toString());
                producer.write(record);
                handler.reply(200);
            } catch (Exception e) {
                handler.fail(500, e.toString());
            }
        });
        vertx.eventBus().<JsonObject>consumer(KAFKA_PUT_LOCATION, handler -> {
            try {
                LocationData loc = new LocationData(handler.body());
                KafkaProducerRecord<String, String> record =
                        KafkaProducerRecord.create(RESP_LOC, loc.getId(), loc.toString());
                producer.write(record);
                handler.reply(200);
            }
            catch (Exception e){
                handler.fail(500, e.toString());
            }

        });
        vertx.eventBus().<JsonObject>consumer(KAFKA_PUT_SEARCH, handler -> {
            //todo проверить
            try {
                JsonObject response = mapper.mapElasticSearchResponce(handler.body());
                KafkaProducerRecord<String, String> record =
                        KafkaProducerRecord.create(SEARCH_PUT, handler.body().getString("id"), response.toString());
                producer.write(record);
                handler.reply(200);
            }
            catch (Exception e){
                handler.fail(500, e.toString());
            }
        });
    }
}

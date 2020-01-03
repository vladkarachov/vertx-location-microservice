package profiles.model;

import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumerRecord;

public class Mapper {
    public JsonObject SuccessResponce(KafkaConsumerRecord<String, String> record){
        return new JsonObject().put("status", "OK").put("key", record.key());
    }
    public JsonObject FalueResponce(KafkaConsumerRecord<String, String> record, String message){
       return new JsonObject()
                .put("key", record.key())
                .put("status", "error")
                .put("message", message);
    }
    public JsonObject mapElasticSearchKafka(KafkaConsumerRecord<String, String> record, LocationData locationData){
        return new JsonObject()
                .put("city", locationData.getCity())
                .put("country", locationData.getCountry())
                .put("key", record.key());
    }

    public JsonObject mapElasticSearchResponce(JsonObject js){
        return new JsonObject().put("city", js.getString("city")).put("country", js.getString("country"));
    }
    public JsonObject mapResponce(JsonObject js){
        return new JsonObject()
                .put("status", js.getString("status"))
                .put("message", js.getString("message"));
    }
    public LocationData recordToLoc(KafkaConsumerRecord<String, String> record){
        return new LocationData(new JsonObject(record.value()).put("id", record.key()));
    }

    public JsonObject locToMongo(LocationData loc){
        return new JsonObject()
                .put("_id", loc.getId())
                .put("latitude", loc.getLatitude())
                .put("longitude", loc.getLongitude())
                .put("city", loc.getCity())
                .put("country", loc.getCountry());
    }

    public LocationData mongoToLoc(JsonObject jsonObject){
        LocationData loc= new LocationData(jsonObject).setId(jsonObject.getString("_id"));
        return loc;
    }
}

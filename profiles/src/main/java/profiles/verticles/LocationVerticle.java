package profiles.verticles;



import com.hazelcast.mapreduce.impl.task.KeyValueSourceMappingPhase;
import com.mongodb.async.client.MongoCollection;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.Json;
import io.vertx.ext.mongo.MongoClient;
import org.bson.Document;
import org.bson.conversions.Bson;
import profiles.model.*;
import vertx.common.MicroserviceVerticle;
import io.vertx.config.ConfigChange;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
//mongo
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;



public class LocationVerticle extends MicroserviceVerticle {

    public static final String GET_LOCATION = "location:get";
    public static final String PUT_LOCATION = "location:put";
    public static final String DELETE_LOCATION = "location:delete";
    public static final String connectionstr="mongodb+srv://mongouser:jy7Mbq1ROXmJ12T1@cluster0-iieo1.mongodb.net/LocationData?&w=majority";
    MongoClient mongoClient;



    @Override
    public void start(Promise<Void> startPromise) {
        createServiceDiscovery();
        registerCodecs();
        setupDB();
        setupListener();
    }
    private void setupDB(){
        try {
            //todo cleanup
            ConnectionString connString = new ConnectionString(
                    connectionstr);
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(connString)
                    .build();
            mongoClient = MongoClient.createShared(vertx, new JsonObject().put("connection_string", connectionstr));

        }
        catch (Exception e){
            System.out.println(e.getCause());

        }


    }

    private void registerCodecs() {
        try {
            vertx.eventBus().registerDefaultCodec(LocationData.class, new LocationDataCodec());
        } catch (IllegalStateException ignored) {}
    }

    private void setupListener() {
        vertx.eventBus().<Buffer>consumer(GET_LOCATION, handler -> {
            JsonObject query = new JsonObject().put("id", handler.body().toString());
            System.out.println(query);
            mongoClient.find("Locations", query, res -> {
                if (res.succeeded()) {
                    try{
                        JsonObject json = res.result().get(0);
                        LocationData locationData = new LocationData(json);
                        handler.reply(locationData);
                    }
                    catch (Exception e){

                        handler.fail(404, "Database error : Not found or handling error");
                    }
                } else {
                    handler.fail(500, res.cause().toString());

                }
            });


        });
        vertx.eventBus().<Buffer>consumer(PUT_LOCATION, handler -> {
            //TODO put in kafka, error handling
            LocationData locationData = (LocationData) handler.body();
            JsonObject document = new JsonObject().put("id", locationData.getId());
                  /*  .put("latitude", locationData.getLatitude())
                    .put("longitude", locationData.getLongitude())
                    .put("city", locationData.getCity())
                    .put("country", locationData.getCountry());

                   */
            System.out.println("a");
            mongoClient.find("Locations", document, sRes -> {

                if (sRes.succeeded()) {
                    try{

                        System.out.println("find");
                        JsonObject jsonObject = sRes.result().get(0);
                        handler.fail(400, "Already exist");
                    }
                    catch (Exception ignored){
                        //everything ok
                        document.put("latitude", locationData.getLatitude())
                                .put("longitude", locationData.getLongitude())
                                .put("city", locationData.getCity())
                                .put("country", locationData.getCountry());
                        mongoClient.save("Locations", document, res -> {
                            if (res.succeeded()) {
                                handler.reply(new JsonObject().put("code", 200));
                            }
                            else {
                                handler.fail(500, res.cause().toString());
                            }
                        });
                    }
                }
                else {
                    handler.fail(500, sRes.cause().toString());
                }
            });



        });
        vertx.eventBus().<Buffer>consumer(DELETE_LOCATION, handler -> {
            String id = handler.body().toString();
            JsonObject query = new JsonObject().put("id", id);
            mongoClient.removeDocument("Locations", query, result -> {
                if (result.succeeded()) {
                    handler.reply(200);
                } else {
                    String fail = "Database error: " + result.cause().toString();
                    handler.fail(1, fail);
                }
            });


        });
    }
}

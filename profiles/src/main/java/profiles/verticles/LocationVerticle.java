package profiles.verticles;



import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.ext.mongo.MongoClient;
import profiles.model.*;
import vertx.common.MicroserviceVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
//mongo
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;

import java.util.concurrent.atomic.AtomicReference;


public class LocationVerticle extends MicroserviceVerticle {

    public static final String GET_LOCATION = "location:get";
    public static final String PUT_LOCATION = "location:put";
    public static final String DELETE_LOCATION = "location:delete";
    public static final String connectionstr="mongodb+srv://mongouser:jy7Mbq1ROXmJ12T1@cluster0-iieo1.mongodb.net/LocationData?&w=majority";
    MongoClient mongoClient;
    Mapper mapper = new Mapper();


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

    private void setupListener(){
        setupListenerGet();
        setupListenerPut();
        setupListenerDelete();
    }

    //listeners
    private void setupListenerGet() {
        vertx.eventBus().<Buffer>consumer(GET_LOCATION, handler -> {
            JsonObject query = new JsonObject().put("_id", handler.body().toString());
            try {
                mongoFindLocation(query, handler);
            } catch (Exception e) {
                handler.fail(500, e.getMessage());
            }
        });
    }
    private void setupListenerPut() {
        vertx.eventBus().<Buffer>consumer(PUT_LOCATION, handler -> {
            LocationData locationData = (LocationData) handler.body();
            //check existence
            try {
                mongoSaveLocation(locationData, handler);
            } catch (Exception e) {
                handler.fail(500, e.getMessage());
            }
        });
    }

    private void setupListenerDelete(){
        vertx.eventBus().<Buffer>consumer(DELETE_LOCATION, handler -> {
            String id = handler.body().toString();
            JsonObject query = new JsonObject().put("_id", id);
            mongoDeleteLocation(query, handler);
        });
    }

    //mongo
    private void mongoFindLocation(JsonObject query, Message<Buffer> handler){
        mongoClient.find("Locations", query, res->{
            if (res.succeeded()) {
                try {
                    JsonObject json = res.result().get(0);
                    LocationData locationData= mapper.mongoToLoc(json);
                    handler.reply(locationData);
                } catch (Exception e) {
                    handler.fail(404, "Nothing Found");

                }
            } else {
                handler.fail(500, res.cause().toString());
            }

        });
    }

    private void mongoSaveLocation(LocationData locationData, Message<Buffer> handler) {
        JsonObject document = mapper.locToMongo(locationData);
        mongoClient.insert("Locations", document, res -> {
            if (res.succeeded()) {
                handler.reply(200);
            } else {
                handler.fail(500, res.cause().toString());
            }
        });
    }

    private void mongoDeleteLocation(JsonObject query, Message<Buffer> handler){
        mongoClient.removeDocument("Locations", query, result -> {
            if (result.succeeded()) {
                handler.reply(200);
            } else {
                String fail = "Database error: " + result.cause().toString();
                handler.fail(500, fail);
            }
        });
    }
}

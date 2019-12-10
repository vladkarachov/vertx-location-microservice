package profiles.verticles;

import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.GeocodingApiRequest;
import com.google.maps.PendingResult;
import com.google.maps.errors.ApiException;
import com.google.maps.model.*;
import io.vertx.core.buffer.Buffer;
import profiles.model.*;
import vertx.common.MicroserviceVerticle;
import io.vertx.config.ConfigChange;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

import java.io.IOException;

public class GoogleAPI extends MicroserviceVerticle {
    public static final String GET_METADATA = "googleapi:get";
    public static final String API_KEY="AIzaSyDcSNRJfEj6CMZiSCL026OP1NJ_9VVM0A8";

    @Override
    public void start(Promise<Void> startPromise) {
        createServiceDiscovery();
        registerCodecs();
        setupListener();
    }

    private void registerCodecs() {
        try {
            vertx.eventBus().registerDefaultCodec(LocationData.class, new LocationDataCodec());
        } catch (IllegalStateException ignored) {}
    }

    private void setupListener() {
        vertx.eventBus().<Buffer>consumer(GET_METADATA, handler -> {

            //https://github.com/googlemaps/google-maps-services-java
            GeoApiContext context = new GeoApiContext.Builder().apiKey(API_KEY).build();
            LocationData locationData= (LocationData) handler.body();
             LatLng loc= new LatLng(locationData.getLatitude(), locationData.getLongitude());
           GeocodingApiRequest req = GeocodingApi.reverseGeocode(context, loc);//.resultType(AddressType.COUNTRY, AddressType.ADMINISTRATIVE_AREA_LEVEL_2);
           //ADMINISTRATIVE_AREA_LEVEL_2 receives districts - not every location has city, especially in different countries (but sometimes it represents a city)
            req.setCallback(new  PendingResult.Callback<GeocodingResult[]>(){
                @Override
                public void onResult(GeocodingResult[] result) {

                    try {
                        for(AddressComponent el:result[0].addressComponents){
                            if (el.types[0].toString() == "country") {
                                locationData.setCountry(el.longName);
                            }
                            if((el.types[0].toString().equals("ADMINISTRATIVE_AREA_LEVEL_2") && locationData.getCity()==null)
                                    || el.types[0].toString().equals("locality") )
                            {
                                locationData.setCity(el.longName);
                            }
                        }
                        //TODO put in message broker
                        handler.reply(locationData);
                    }
                    catch (Exception e){
                        handler.fail(1, "Empty Google cloud responce");
                    }

                }

                @Override
                public void onFailure(Throwable e) {
                    handler.fail(1, e.getMessage());
                }

            });


        });
    }


}




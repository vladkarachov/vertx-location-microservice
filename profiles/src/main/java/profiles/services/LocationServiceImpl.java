package profiles.services;

import Location.LocationObject;
import Location.LocationServiceGrpc;
import Location.idObj;
import Location.resp;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.StatusException;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import profiles.model.LocationData;
import profiles.verticles.GoogleAPI;
import profiles.verticles.LocationVerticle;
import profiles.verticles.ProfilesVerticle;

import javax.annotation.Nonnull;

import java.rmi.registry.LocateRegistry;

import static profiles.verticles.LocationVerticle.GET_LOCATION;
import static profiles.verticles.GoogleAPI.GET_METADATA;
import static profiles.verticles.LocationVerticle.PUT_LOCATION;

public class LocationServiceImpl extends LocationServiceGrpc.LocationServiceImplBase {

    private final Vertx mVertx;

    // Constructors

    /**
     * Set custom vert.x instance
     */
    public LocationServiceImpl(@Nonnull Vertx vertx) {
        mVertx = vertx;
    }

    @Override
    public void getLocation(idObj request, StreamObserver<LocationObject> responseObserver){
        Buffer buf = Buffer.buffer();
        buf.appendString(request.getId());
        mVertx.eventBus().request(GET_LOCATION, buf, ar -> {
            if (ar.failed()) {
                //как код ошибки ему поставить
                System.out.println(ar.cause());
                StatusException failure = failed(ar.cause());
                responseObserver.onError(failure);
                return;
            }
            LocationData loc = (LocationData) ar.result().body();
            LocationObject response= transformToLocationData(loc);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        });
    }

    @Override
    public void addLocation(LocationObject request, StreamObserver<resp> responseObserver){
        LocationData loc= new LocationData.Builder()
                .setId(request.getId())
                .setLatitude(request.getLatitude())
                .setLongitude(request.getLongitude())
                .build();
        mVertx.eventBus().request(GET_METADATA, loc, ar->{
            if(ar.failed()){
                System.out.println(ar.cause());
                StatusException failure = failed(ar.cause());
                responseObserver.onError(failure);
                return;
            }
            //TODO
            LocationData loc2 = (LocationData) ar.result().body();
            System.out.println(loc2.toString());

            mVertx.eventBus().request(PUT_LOCATION, loc2, handl->{
                if(handl.failed()){
                    StatusException failure = failed(handl.cause());
                    responseObserver.onError(failure);
                    return;
                }
                resp responce = resp.newBuilder().setCode(200).build();
                responseObserver.onNext(responce);
                responseObserver.onCompleted();
            });

       });
    }

    private LocationObject transformToLocationData(LocationData loc) {
        LocationObject obj;
         if(loc.getCity()!=null && loc.getCountry()!=null){
             obj= LocationObject.newBuilder().setId(loc.getId())
                    .setLatitude(loc.getLatitude())
                    .setLongitude(loc.getLatitude())
                    .setCity(loc.getCity())
                    .setCountry(loc.getCountry())
                    .build();
        }
         else {
              obj= LocationObject.newBuilder().setId(loc.getId())
                     .setLatitude(loc.getLatitude())
                     .setLongitude(loc.getLatitude())
                     .build();

         }
        return obj;
    }

    private StatusException failed(Throwable cause) {
        Status status = Status.newBuilder()
                .setCode(Code.UNAVAILABLE_VALUE)
                .setMessage(cause.getMessage())
                .build();

        return StatusProto.toStatusException(status);
    }
}
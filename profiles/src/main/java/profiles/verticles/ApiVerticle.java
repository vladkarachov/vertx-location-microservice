package profiles.verticles;

import profiles.model.*;
import profiles.services.LocationServiceImpl;
import profiles.services.ProfileServiceImpl;
import vertx.common.MicroserviceVerticle;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.VertxServer;
import io.vertx.grpc.VertxServerBuilder;

import javax.annotation.Nonnull;
import java.io.File;

import static profiles.verticles.ConfigurationVerticle.EBA_CONFIG_FETCH;
import static profiles.verticles.ConfigurationVerticle.EBA_CONFIG_UPDATE;

public class ApiVerticle extends MicroserviceVerticle {

  // Variables

  private VertxServer mServer;

  // Overrides

  @Override
  public void start() {
    createServiceDiscovery();
    registerCodecs();
    setupConfigListener();
    setupConfig();
  }

  // Private

  /**
   * Set our channels of communication using Config and Profile classes
   * and codecs for them
   */
  private void registerCodecs() {
    try {
      vertx.eventBus().registerDefaultCodec(Config.class, new ConfigMessageCodec());
      vertx.eventBus().registerDefaultCodec(LocationData.class, new LocationDataCodec());
    } catch (IllegalStateException ignored) {}
  }

  /** Listen on configuration changes and update API server accordingly */
  private void setupConfigListener() {
    vertx.eventBus().<Config>consumer(EBA_CONFIG_UPDATE, configAr -> {
      setupServer(configAr.body()).future().setHandler(serverAr -> {
        if (serverAr.failed()) {
          System.out.println("API server restart failed: " + serverAr.cause().getMessage());
        } else {
          System.out.println("API server restarted on " + configAr.body().getEndpointHost() + ":" + configAr.body().getEndpointPort());
        }
      });
    });
  }

  private void setupConfig() {
    Promise<Config> promise = Promise.promise();
    promise.future().setHandler(configAr -> {
      if (configAr.failed()) {
        System.out.println("API server start failed: " + configAr.cause().getMessage());
      } else {
        System.out.println("API server started on " + configAr.result().getEndpointHost() + ":" + configAr.result().getEndpointPort());
      }
    });
    fetchConfig(promise);
  }

  /** Get config from eventbus and pass it to promise */
  private void fetchConfig(Promise<Config> promise) {
    vertx.eventBus().<Config>request(EBA_CONFIG_FETCH, new JsonObject(), configAr -> {
      if (configAr.failed()) {
        promise.fail(configAr.cause());
        return;
      }

      Config config = configAr.result().body();
      setupServer(config).future().setHandler(serverAr -> {
        if (serverAr.failed()) {
          promise.fail(serverAr.cause());
        } else {
          promise.complete(config);
        }
      });
    });
  }

  private Promise<Void> setupServer(@Nonnull Config config) {
    Promise<Void> promise = Promise.promise();

    if (mServer != null) mServer.shutdown();

    mServer = VertxServerBuilder.forAddress(vertx, config.getEndpointHost(), Integer.parseInt(config.getEndpointPort()))
      .useTransportSecurity(certChainFile(config), privateKeyFile(config))
            /** Important, adding service for retrieval of Profile data via gRPC*/
      //.addService(new ProfileServiceImpl(vertx))
       .addService(new LocationServiceImpl(vertx))
      .addService(ProtoReflectionService.newInstance())
      .build()
      .start(ar -> {
        if (ar.failed()) {
          promise.fail(ar.cause());
        } else {
          publishHttpEndpoint(
            "API endpoint",
            config.getEndpointHost(),
            Integer.parseInt(config.getEndpointPort()),
            publishAr -> {});

          promise.complete();
        }
      });

    return promise;
  }

  private File certChainFile(@Nonnull Config config) {
    return new File(config.getTlsCertChain());
  }

  private File privateKeyFile(@Nonnull Config config) {
    return new File(config.getTlsPrivKey());
  }
}

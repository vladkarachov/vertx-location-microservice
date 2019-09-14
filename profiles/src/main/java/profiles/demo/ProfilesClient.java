package profiles.demo;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import profile.ProfileObject;
import profile.ProfileRequest;
import profile.ProfileServiceGrpc;
import profiles.model.Config;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SSLException;
import java.io.File;

public class ProfilesClient {

  // Constants

  private static final String configPath = "conf/config.json";

  // Main

  public static void main(String[] args) {
    getRetriever(configPath).getConfig(ar -> {
      if (ar.failed()) throw new RuntimeException("Configuration failed: " + ar.cause().getMessage());

      Config config = new Config(ar.result());
      ProfileServiceGrpc.ProfileServiceBlockingStub stub = getService(config);

      try {
        ProfileRequest request = ProfileRequest
          .newBuilder()
          .setId("some id")
          .build();

        ProfileObject profile = stub
          .getProfile(request)
          .getProfile();

        System.out.println("RESPONSE IS: " + profile.toString());
      } catch (StatusRuntimeException e) {
        System.out.println("ERROR: " + e.getStatus().toString() + ", " + e.getMessage());
      }
    });
  }

  // Private

  private static ConfigRetriever getRetriever(@Nonnull String path) {
    JsonObject configPath = new JsonObject()
      .put("path", path);

    ConfigStoreOptions configStoreOptions = new ConfigStoreOptions()
      .setType("file")
      .setConfig(configPath);

    ConfigRetrieverOptions retrieverOptions = new ConfigRetrieverOptions()
      .addStore(configStoreOptions);

    return ConfigRetriever.create(Vertx.vertx(), retrieverOptions);
  }

  private static SslContext getSslContext(@Nonnull Config config) {
    File trustCert = new File(config.getTlsCa());

    try {
      return GrpcSslContexts.forClient()
        .trustManager(trustCert)
        .build();
    } catch (SSLException e) {
      throw new RuntimeException(e.getCause());
    }
  }

  private static ProfileServiceGrpc.ProfileServiceBlockingStub getService(@Nonnull Config config) {
    String endpointHost = config.getEndpointHost();
    int endpointPort = Integer.parseInt(config.getEndpointPort());
    SslContext sslContext = getSslContext(config);

    ManagedChannel channel = NettyChannelBuilder
      .forAddress(endpointHost, endpointPort)
      .sslContext(sslContext)
      .build();

    return ProfileServiceGrpc.newBlockingStub(channel);
  }
}

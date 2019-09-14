package vertx.common;

import io.vertx.core.*;
import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.Router;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;
import io.vertx.servicediscovery.types.EventBusService;
import io.vertx.servicediscovery.types.HttpEndpoint;
import io.vertx.servicediscovery.types.MessageSource;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MicroserviceVerticle extends AbstractVerticle {

  // Variables

  private ServiceDiscovery mDiscovery;
  private Set<Record> mRegisteredRecords = new ConcurrentHashSet<>();

  // Overrides

  @Override
  public void stop(Promise<Void> stopFuture) {
    List<Promise> promises = mRegisteredRecords
      .stream()
      .map(this::unpublish)
      .collect(Collectors.toList());

    if (promises.isEmpty()) {
      stopDiscovery(stopFuture);
    } else {
      stopServices(promises, stopFuture);
    }
  }

  // Public

  protected void publishHttpEndpoint(String endpoint, String host, int port, Handler<AsyncResult<Void>> completion) {
    JsonObject filter = new JsonObject()
      .put("name", endpoint);
    mDiscovery.getRecord(filter, ar -> {
      if (!ar.succeeded() || ar.result() == null) {
        Record record = HttpEndpoint.createRecord(endpoint, host, port, "/");
        publish(record, completion);
      }
    });
  }

  protected void publishMessageSource(String name, String address, Handler<AsyncResult<Void>> completionHandler) {
    JsonObject filter = new JsonObject()
      .put("name", name);
    mDiscovery.getRecord(filter, ar -> {
      if (!ar.succeeded() || ar.result() == null) {
        Record record = MessageSource.createRecord(name, address);
        publish(record, completionHandler);
      }
    });
  }

  protected void publishMessageSource(String name, String address, Class<?> contentClass, Handler<AsyncResult<Void>> completionHandler) {
    JsonObject filter = new JsonObject()
      .put("name", name);
    mDiscovery.getRecord(filter, ar -> {
      if (!ar.succeeded() || ar.result() == null) {
        Record record = MessageSource.createRecord(name, address, contentClass);
        publish(record, completionHandler);
      }
    });
  }

  protected void publishEventBusService(String name, String address, Class<?> serviceClass, Handler<AsyncResult<Void>> completionHandler) {
    JsonObject filter = new JsonObject()
      .put("name", name);
    mDiscovery.getRecord(filter, ar -> {
      if (!ar.succeeded() || ar.result() == null) {
        Record record = EventBusService.createRecord(name, address, serviceClass);
        publish(record, completionHandler);
      }
    });
  }

  protected ServiceDiscovery getDiscovery() {
    return mDiscovery;
  }

  // Private

  protected void createHealthCheck() {
    HealthChecks hc = HealthChecks.create(vertx);
    hc.register("Microservice", 5000, future -> future.complete(Status.OK()));

    HealthCheckHandler healthCheckHandler = HealthCheckHandler.create(vertx);
    Router router = Router.router(vertx);
    router.get("/health*").handler(healthCheckHandler);
    vertx.createHttpServer()
      .requestHandler(router)
      .listen(4000, ar -> {
        if (ar.failed()) {
          System.out.println("Health check server failed to start: " + ar.cause().getMessage());
        } else {
          System.out.println("Health check server started on 4000");
        }
      });
  }

  protected void createServiceDiscovery() {
    JsonObject config = config();
    ServiceDiscoveryOptions opts = new ServiceDiscoveryOptions()
      .setBackendConfiguration(config);
    mDiscovery = ServiceDiscovery.create(vertx, opts);
  }

  private void publish(Record record, Handler<AsyncResult<Void>> completion) {
    mDiscovery.publish(record, ar -> {
      if (ar.succeeded()) mRegisteredRecords.add(record);

      completion.handle(ar.map((Void)null));
    });
  }

  private Promise<Void> unpublish(Record record) {
    mRegisteredRecords.remove(record);

    Promise<Void> unregisteringFuture = Promise.promise();
    mDiscovery.unpublish(record.getRegistration(), unregisteringFuture);

    return unregisteringFuture;
  }

  private void stopDiscovery(Promise<Void> stopPromise) {
    mDiscovery.close();
    stopPromise.complete();
  }

  private void stopServices(List<Promise> promises, Promise<Void> stopPromise) {
    List<Future> futures = promises
      .stream()
      .map((Function<Promise, Future>) Promise::future)
      .collect(Collectors.toList());

    CompositeFuture.all(futures)
      .setHandler(ar -> {
        mDiscovery.close();

        if (ar.failed()) {
          stopPromise.fail(ar.cause());
        } else {
          stopPromise.complete();
        }
      });
  }
}


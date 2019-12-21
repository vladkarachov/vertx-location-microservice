package profiles.model;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonObject;


public class LocationDataCodec implements MessageCodec<LocationData, LocationData> {

    @Override
    public void encodeToWire(Buffer buffer, LocationData locs) {
        String str = locs.toJson()
                .encode();
        int length = str.getBytes().length;
        buffer.appendInt(length);
        buffer.appendString(str);
    }



   @Override
    public LocationData decodeFromWire(int pos, Buffer buffer) {
        int position = pos;
        int length = buffer.getInt(position);
        String jsonStr = buffer.getString(position += 4, position += length);
        JsonObject json = new JsonObject(jsonStr);

        return new LocationData(json);
    }

    @Override
    public LocationData transform(LocationData locationData) {
        return locationData;
    }


    @Override
    public String name() {
        return getClass().getSimpleName();
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }

}

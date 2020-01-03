package profiles.model;



import io.vertx.core.json.JsonObject;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;



public class LocationData {

    private String      mId;
    private double      mLatitude;
    private double      mLongitude;
    private String      mCity;
    private String      mCountry;


    private JsonObject  mJson;
    private final String LATITUDE="latitude";
    private final String LONGITUDE="longitude";
    private final String ID="id";
    private final String CITY="city";
    private final String COUNTRY="country";


    public LocationData(@Nonnull JsonObject json) {

        mId = json.getString(ID);
        mLatitude = json.getDouble(LATITUDE);
        mLongitude = json.getDouble(LONGITUDE);
        mCity=json.getString(CITY);
        mCountry=json.getString(COUNTRY);
        mJson = this.toJson();
    }

    public LocationData(@Nonnull String id, @Nonnull double latitude, @Nullable double longitude, String city, String country) {
        mJson = new JsonObject()
                .put(ID, id)
                .put(LATITUDE, latitude)
                .put(LONGITUDE, longitude)
                .put(CITY, city)
                .put(COUNTRY, country);


        mLatitude = latitude;
        mLongitude = longitude;
        mId = id;
        mCity=city;
        mCountry=country;
    }

    public LocationData(@Nonnull String id, @Nonnull double latitude, @Nullable double longitude) {
        mJson = new JsonObject()
                .put(ID, id)
                .put(LATITUDE, latitude)
                .put(LONGITUDE, longitude)
                ;


        mLatitude = latitude;
        mLongitude = longitude;
        mId = id;

    }

    public JsonObject toJson() {
        return new JsonObject()
                .put(ID, mId)
                .put(LATITUDE, mLatitude)
                .put(LONGITUDE, mLongitude)
                .put(CITY, mCity)
                .put(COUNTRY, mCountry);
    }

    public Double getLatitude() {
        return mLatitude;
    }

    public Double getLongitude() {
        return mLongitude;
    }

    public String getId() {
        return mId;
    }

    public String getCity() {return mCity;}

    public String getCountry() {return mCountry;}

    public void setCity(String city) {
        this.mCity = city;
    }

    public void setCountry(String country) {
        this.mCountry = country;
    }

    public LocationData setId(String id) {this.mId= id;
        return this;
    }
    @Override
    public String toString() {

        return mJson.toString();
        //это в другую сторону не разбирается)))
//        return "{" +
//                "id:'" + mId + '\'' +
//                ", latitude:'" + mLatitude + '\'' +
//                ", longitude:'" + mLongitude+ '\'' +
//                ", country:" + mCountry +
//                ", city:" + mCity +
//                '}';
    }

    public static class Builder {

        private double mLatitude;
        private double mLongitude;
        private String mId;

        public Builder() {


        }

        public Builder setId(String id) {
            mId = id;

            return this;
        }
        public Builder setLatitude(double latitude) {
            mLatitude = latitude;
            return this;
        }
        public Builder setLongitude(double longitude){
            mLongitude = longitude;
            return this;
        }
        public LocationData build() {
            return new LocationData(mId, mLatitude, mLongitude);
        }

}
}

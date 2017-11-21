package com.peterlaurence.trekadvisor.core.map;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Parcel;
import android.support.annotation.Nullable;
import android.util.Log;

import com.peterlaurence.trekadvisor.core.map.gson.MapGson;
import com.peterlaurence.trekadvisor.core.map.gson.MarkerGson;
import com.peterlaurence.trekadvisor.core.map.gson.RouteGson;
import com.peterlaurence.trekadvisor.core.map.maploader.MapLoader;
import com.peterlaurence.trekadvisor.core.projection.Projection;
import com.peterlaurence.trekadvisor.core.projection.ProjectionTask;
import com.peterlaurence.trekadvisor.util.Tools;
import com.peterlaurence.trekadvisor.util.ZipTask;
import com.qozix.tileview.graphics.BitmapProvider;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * A {@code Map} contains all the information that defines a map. That includes :
 * <ul>
 * <li>The name that will appear in the map choice list </li>
 * <li>The directory that contains image data and configuration file </li>
 * <li>The calibration method along with calibration points </li>
 * <li>Points of interest </li>
 * ...
 * </ul>
 *
 * @author peterLaurence
 */
public class Map {
    private static final String TAG = "Map";
    private static final String UNDEFINED = "undefined";
    /* The configuration file of the map, named map.json */
    private final File mConfigFile;
    private final Bitmap mImage;
    private BitmapProvider mBitmapProvider;
    private MapBounds mMapBounds;
    /* The Java Object corresponding to the json configuration file */
    private MapGson mMapGson;
    /* The Java Object corresponding to the json file of markers */
    private MarkerGson mMarkerGson;
    /* The Java Object corresponding to the json file of routes */
    private RouteGson mRouteGson;
    private CalibrationStatus mCalibrationStatus = CalibrationStatus.NONE;

    /**
     * To create a {@link Map}, three parameters are needed. <br>
     * The {@link MarkerGson} is set later when the user wants to see the map.
     *
     * @param mapGson   the {@link MapGson} object that includes informations relative to levels,
     *                  the tile size, the name of the map, etc.
     * @param jsonFile  the {@link File} for serialization.
     * @param thumbnail the {@link File} image for map customization.
     */
    public Map(MapGson mapGson, File jsonFile, File thumbnail) {
        mMapGson = mapGson;
        mMarkerGson = new MarkerGson();
        mRouteGson = new RouteGson();
        mConfigFile = jsonFile;
        mImage = getBitmapFromFile(thumbnail);
    }

    protected Map(Parcel in) {
        mConfigFile = new File(in.readString());
        mImage = in.readParcelable(Bitmap.class.getClassLoader());
    }

    private static
    @Nullable
    Bitmap getBitmapFromFile(File file) {
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        if (file != null) {
            return BitmapFactory.decodeFile(file.getAbsolutePath(), bmOptions);
        }
        return null;
    }

    public Bitmap getDownSample() {
        // TODO : parametrize that
        File imageFile = new File(mConfigFile.getParentFile(), "down_sample.jpg");
        try {
            return BitmapFactory.decodeFile(imageFile.getPath());
        } catch (OutOfMemoryError | Exception e) {
            // maybe log here
        }
        return null;
    }

    public
    @Nullable
    String getProjectionName() {
        if (mMapGson.calibration != null && mMapGson.calibration.projection != null) {
            return mMapGson.calibration.projection.getName();
        }
        return null;
    }

    public
    @Nullable
    Projection getProjection() {
        try {
            return mMapGson.calibration.projection;
        } catch (NullPointerException e) {
            return null;
        }
    }

    public void setProjection(Projection projection) {
        mMapGson.calibration.projection = projection;
    }

    public BitmapProvider getBitmapProvider() {
        return mBitmapProvider;
    }

    public void setBitmapProvider(BitmapProvider bitmapProvider) {
        mBitmapProvider = bitmapProvider;
    }

    public void clearCalibrationPoints() {
        mMapGson.calibration.calibration_points.clear();
    }

    /**
     * Get the bounds the map. See {@link MapBounds}.
     */
    public
    @Nullable
    MapBounds getMapBounds() {
        return mMapBounds;
    }

    public void calibrate() {
        /* Init the projection */
        if (mMapGson.calibration.projection != null) {
            mMapGson.calibration.projection.init();
        }

        List<MapGson.Calibration.CalibrationPoint> calibrationPoints = mMapGson.calibration.calibration_points;
        if (calibrationPoints == null) return;
        switch (getCalibrationMethod()) {
            case SIMPLE_2_POINTS:
                if (calibrationPoints.size() >= 2) {
                    /* Correct points if necessary */
                    MapCalibrator.sanityCheck2PointsCalibration(calibrationPoints.get(0),
                            calibrationPoints.get(1));

                    mMapBounds = MapCalibrator.simple2PointsCalibration(calibrationPoints.get(0),
                            calibrationPoints.get(1));
                }
                break;
            case CALIBRATION_3_POINTS:
                if (calibrationPoints.size() >= 3) {
                    mMapBounds = MapCalibrator.calibrate3Points(calibrationPoints.get(0),
                            calibrationPoints.get(1), calibrationPoints.get(2));
                }
                break;
            default:
                // don't care
        }

        /* Update the calibration status */
        setCalibrationStatus();
    }

    private void setCalibrationStatus() {
        // TODO : implement the detection of an erroneous calibration
        if (mMapGson.calibration.calibration_points.size() >= 2) {
            mCalibrationStatus = CalibrationStatus.OK;
        } else {
            mCalibrationStatus = CalibrationStatus.NONE;
        }
    }

    /**
     * Get the projected values for a geographical position. <br>
     * This utility method is a blocking call. It can also be done asynchronously by getting the
     * {@link Projection} with {@link Map#getProjection()}, create a
     * {@link com.peterlaurence.trekadvisor.core.projection.ProjectionTask} and implement a
     * {@link ProjectionTask.ProjectionUpdateLister}.
     *
     * @param latitude  the geodetic latitude
     * @param longitude the geodetic longitude
     * @return the [X ; Y] values, or null if this map
     */
    @Nullable
    public double[] getProjectedValues(double latitude, double longitude) {
        Projection projection = getProjection();
        if (projection == null) {
            return null;
        } else {
            return projection.doProjection(latitude, longitude);
        }
    }

    /**
     * @return the {@link File} which is the folder containing the map.
     */
    public File getDirectory() {
        return mConfigFile.getParentFile();
    }

    public String getName() {
        return mMapGson.name;
    }

    public void setName(String newName) {
        mMapGson.name = newName;
    }

    public String getDescription() {
        return "";
    }

    /**
     * The calibration status is either : <ul>
     * <li>{@link CalibrationStatus#OK}</li>
     * <li>{@link CalibrationStatus#NONE}</li>
     * <li>{@link CalibrationStatus#ERROR}</li>
     * </ul>.
     */
    public CalibrationStatus getCalibrationStatus() {
        return mCalibrationStatus;
    }

    /**
     * Add a new route to the map.
     */
    public void addRoute(RouteGson.Route route) {
        if (mRouteGson != null) {
            mRouteGson.routes.add(route);
        }
    }

    /**
     * Add a new marker.
     */
    public void addMarker(MarkerGson.Marker marker) {
        mMarkerGson.markers.add(marker);
    }

    public Bitmap getImage() {
        return mImage;
    }

    public List<MapGson.Level> getLevelList() {
        return mMapGson.levels;
    }

    public MapLoader.CALIBRATION_METHOD getCalibrationMethod() {
        return MapLoader.CALIBRATION_METHOD.fromCalibrationName(
                mMapGson.calibration.calibration_method);
    }

    public void setCalibrationMethod(MapLoader.CALIBRATION_METHOD method) {
        mMapGson.calibration.calibration_method = method.name();
    }

    public String getOrigin() {
        try {
            return mMapGson.provider.generated_by;
        } catch (NullPointerException e) {
            return UNDEFINED;
        }
    }

    /**
     * Get the number of calibration that should be defined.
     */
    public int getCalibrationPointsNumber() {
        switch (getCalibrationMethod()) {
            case SIMPLE_2_POINTS:
                return 2;
            case CALIBRATION_3_POINTS:
                return 3;
            default:
                return 0;
        }
    }

    /**
     * Get a copy of the calibration points. <br>
     * This returns only a copy to ensure that no modification is made to the calibration points
     * through this call.
     */
    public List<MapGson.Calibration.CalibrationPoint> getCalibrationPoints() {
        return new ArrayList<>(mMapGson.calibration.calibration_points);
    }

    /**
     * Set the calibration points.
     */
    public void setCalibrationPoints(List<MapGson.Calibration.CalibrationPoint> calibrationPoints) {
        mMapGson.calibration.calibration_points = calibrationPoints;
    }

    public void addCalibrationPoint(MapGson.Calibration.CalibrationPoint point) {
        mMapGson.calibration.calibration_points.add(point);
    }

    public String getImageExtension() {
        return mMapGson.provider.image_extension;
    }

    public int getWidthPx() {
        return mMapGson.size.x;
    }

    public int getHeightPx() {
        return mMapGson.size.y;
    }

    public final MapGson getMapGson() {
        return mMapGson;
    }

    public final MarkerGson getMarkerGson() {
        return mMarkerGson;
    }

    public void setMarkerGson(MarkerGson markerGson) {
        mMarkerGson = markerGson;
    }

    public final RouteGson getRouteGson() {
        return mRouteGson;
    }

    public void setRouteGson(RouteGson routeGson) {
        mRouteGson = routeGson;
    }

    public boolean areMarkersDefined() {
        return mMarkerGson != null && mMarkerGson.markers.size() > 0;
    }

    public boolean areRoutesDefined() {
        return mRouteGson != null && mRouteGson.routes.size() > 0;
    }

    @Nullable
    public List<MarkerGson.Marker> getMarkers() {
        if (mMarkerGson != null) {
            return mMarkerGson.markers;
        }
        return null;
    }

    @Nullable
    public List<RouteGson.Route> getRoutes() {
        if (mRouteGson != null) {
            return mRouteGson.routes;
        }
        return null;
    }

    public final File getConfigFile() {
        return mConfigFile;
    }

    /**
     * For instance, the {@link MapLoader} is designed so that two different maps can't have the
     * same config file path.
     *
     * @return the unique id that identifies the {@link Map}. It can later be used to retrieve the
     * {@link Map} instance from the {@link MapLoader}.
     */
    public int getId() {
        return mConfigFile.getPath().hashCode();
    }

    /**
     * Archives the map. <p>
     * Creates a zip file named with this {@link Map} name and the date. This file is placed in the
     * parent folder of the {@link Map}.
     *
     * @param listener The {@link com.peterlaurence.trekadvisor.util.ZipTask.ZipProgressionListener}.
     */
    public void zip(ZipTask.ZipProgressionListener listener) {
        /* Generate an output zip file named with the map name and the date */
        Date date = new Date();
        DateFormat dateFormat = new SimpleDateFormat("dd\\MM\\yyyy-HH:mm:ss", Locale.ENGLISH);
        String zipFileName = getName() + "-" + dateFormat.format(date) + ".zip";

        /* By default and for instance, the archive is placed in the parent folder of the map */
        File zipDirectory = mConfigFile.getParentFile().getParentFile();
        File outputFile = new File(zipDirectory, zipFileName);
        try {
            if (!outputFile.createNewFile()) {
                listener.onZipError();
            }
        } catch (IOException e) {
            Log.e(TAG, Tools.stackTraceToString(e));
            listener.onZipError();
        }

        ZipTask zipTask = new ZipTask(mConfigFile.getParentFile(), outputFile, listener);
        zipTask.execute();
    }

    public enum CalibrationStatus {
        OK, NONE, ERROR
    }

    /**
     * A MapBounds object holds the bounds coordinates of :
     * <ul>
     * <li>The top-left corner of the map : (projectionX0, projectionY0) or (lon0, lat0) depending
     * on the map using a projection or not. </li>
     * <li>The bottom-right corner of the map : (projectionX1, projectionY1) or (lon1, lat1) </li>
     * </ul>
     */
    public static class MapBounds {
        static double DELTA = 0.0000001;
        public double X0;
        public double Y0;
        public double X1;
        public double Y1;

        public MapBounds(double x0, double y0, double x1, double y1) {
            X0 = x0;
            Y0 = y0;
            X1 = x1;
            Y1 = y1;
        }

        private static boolean doubleIsEqual(double d1, double d2, double delta) {
            if (Double.compare(d1, d2) == 0) {
                return true;
            }
            if ((Math.abs(d1 - d2) <= delta)) {
                return true;
            }

            return false;
        }

        public boolean compareTo(double x0, double y0, double x1, double y1) {
            return doubleIsEqual(X0, x0, DELTA) && doubleIsEqual(Y0, y0, DELTA) &&
                    doubleIsEqual(X1, x1, DELTA) && doubleIsEqual(Y1, y1, DELTA);
        }
    }
}

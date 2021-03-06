package com.peterlaurence.trekadvisor.core.map.gson;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peterLaurence on 30/04/17.
 */
public class MarkerGson {
    public List<Marker> markers;

    public MarkerGson() {
        markers = new ArrayList<>();
    }

    public static class Marker {
        public String name;
        public double lat;
        public double lon;
        public Double proj_x;
        public Double proj_y;
        public String comment;
    }
}

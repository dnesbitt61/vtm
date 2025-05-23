/*
 * Copyright 2014 Hannes Janetzek
 * Copyright 2017-2022 devemux86
 * Copyright 2018 boldtrn
 *
 * This file is part of the OpenScienceMap project (http://www.opensciencemap.org).
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.oscim.tiling.source.mvt;

import edu.colorado.cires.cmg.mvt.adapt.jts.MvtReader;
import edu.colorado.cires.cmg.mvt.adapt.jts.TagKeyValueMapConverter;
import edu.colorado.cires.cmg.mvt.adapt.jts.model.JtsLayer;
import edu.colorado.cires.cmg.mvt.adapt.jts.model.JtsMvt;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.oscim.core.MapElement;
import org.oscim.core.Tag;
import org.oscim.core.Tile;
import org.oscim.tiling.ITileDataSink;
import org.oscim.tiling.source.ITileDecoder;
import org.oscim.utils.Parameters;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class TileDecoder implements ITileDecoder {

    /**
     * Reduce points on-the-fly while reading from vector maps.
     */
    public static int SIMPLIFICATION_MIN_ZOOM = 8;
    public static int SIMPLIFICATION_MAX_ZOOM = 11;

    private final String mLocale;

    private static final float REF_TILE_SIZE = 4096.0f;
    private float mScale;

    private final GeometryFactory mGeomFactory;
    private final MapElement mMapElement;
    private ITileDataSink mTileDataSink;

    public TileDecoder() {
        this("");
    }

    public TileDecoder(String locale) {
        mLocale = locale;
        mGeomFactory = new GeometryFactory();
        mMapElement = new MapElement();
        mMapElement.layer = 5;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean decode(Tile tile, ITileDataSink sink, InputStream is)
            throws IOException {

        mTileDataSink = sink;
        mScale = REF_TILE_SIZE / Tile.SIZE;

        JtsMvt jtsMvt = MvtReader.loadMvt(
                is,
                mGeomFactory,
                new TagKeyValueMapConverter(),
                MvtReader.RING_CLASSIFIER_V1);


        for (JtsLayer layer : jtsMvt.getLayers()) {
            for (Geometry geometry : layer.getGeometries()) {
                parseGeometry(layer.getName(), geometry, (Map<String, Object>) geometry.getUserData(), tile.zoomLevel);
            }
        }

        return true;
    }

    private void parseGeometry(String layerName, Geometry geometry, Map<String, Object> tags, int zoomLevel) {
        mMapElement.clear();
        mMapElement.tags.clear();

        parseTags(tags, layerName);
        if (mMapElement.tags.size() == 0) {
            return;
        }

        boolean err = false;
        if (geometry instanceof Point) {
            mMapElement.startPoints();
            processCoordinateArray(geometry.getCoordinates(), false);
        } else if (geometry instanceof MultiPoint) {
            MultiPoint multiPoint = (MultiPoint) geometry;
            for (int i = 0; i < multiPoint.getNumGeometries(); i++) {
                mMapElement.startPoints();
                processCoordinateArray(multiPoint.getGeometryN(i).getCoordinates(), false);
            }
        } else if (geometry instanceof LineString) {
            processLineString((LineString) geometry);
        } else if (geometry instanceof MultiLineString) {
            MultiLineString multiLineString = (MultiLineString) geometry;
            for (int i = 0; i < multiLineString.getNumGeometries(); i++) {
                processLineString((LineString) multiLineString.getGeometryN(i));
            }
        } else if (geometry instanceof Polygon) {
            processPolygon((Polygon) simplify(geometry, zoomLevel));
        } else if (geometry instanceof MultiPolygon) {
            MultiPolygon multiPolygon = (MultiPolygon) geometry;
            for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
                processPolygon((Polygon) simplify(multiPolygon.getGeometryN(i), zoomLevel));
            }
        } else {
            err = true;
        }

        if (!err) {
            mTileDataSink.process(mMapElement);
        }
    }

    private void processLineString(LineString lineString) {
        mMapElement.startLine();
        processCoordinateArray(lineString.getCoordinates(), false);
    }

    private void processPolygon(Polygon polygon) {
        mMapElement.startPolygon();
        processCoordinateArray(polygon.getExteriorRing().getCoordinates(), true);
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            mMapElement.startHole();
            processCoordinateArray(polygon.getInteriorRingN(i).getCoordinates(), true);
        }
    }

    private void processCoordinateArray(Coordinate[] coordinates, boolean removeLast) {
        int length = removeLast ? coordinates.length - 1 : coordinates.length;
        for (int i = 0; i < length; i++) {
            mMapElement.addPoint((float) coordinates[i].x / mScale, (float) coordinates[i].y / mScale);
        }
    }

    private void parseTags(Map<String, Object> map, String layerName) {
        mMapElement.tags.add(new Tag("layer", layerName));
        boolean hasName = false;
        String fallbackName = null;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String val = (value instanceof String) ? (String) value : String.valueOf(value);
            if (key.startsWith(Tag.KEY_NAME)) {
                int len = key.length();
                if (len == 4) {
                    fallbackName = val;
                    continue;
                }
                if (len < 7)
                    continue;
                if (mLocale.equals(key.substring(5))) {
                    hasName = true;
                    mMapElement.tags.add(new Tag(Tag.KEY_NAME, val, false));
                }
            } else {
                mMapElement.tags.add(new Tag(key, val));
            }
        }
        if (!hasName && fallbackName != null)
            mMapElement.tags.add(new Tag(Tag.KEY_NAME, fallbackName, false));
    }

    private Geometry simplify(Geometry geometry, int zoomLevel) {
        if (Parameters.SIMPLIFICATION_TOLERANCE > 0
                && zoomLevel >= SIMPLIFICATION_MIN_ZOOM && zoomLevel <= SIMPLIFICATION_MAX_ZOOM) {
            if (!mMapElement.tags.contains(Parameters.SIMPLIFICATION_EXCEPTIONS))
                return TopologyPreservingSimplifier.simplify(geometry, Parameters.SIMPLIFICATION_TOLERANCE * 10);
        }
        return geometry;
    }
}

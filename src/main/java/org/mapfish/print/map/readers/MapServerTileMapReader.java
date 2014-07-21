package org.mapfish.print.map.readers;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mapfish.print.RenderingContext;
import org.mapfish.print.Transformer;
import org.mapfish.print.map.renderers.TileRenderer;
import org.mapfish.print.utils.PJsonArray;
import org.mapfish.print.utils.PJsonObject;

/**
 * Support the mapserver tile layout tile=x+y+z
 */
public class MapServerTileMapReader extends TileableMapReader {
    public static class Factory implements MapReaderFactory {
        @Override
        public List<? extends MapReader> create(String type, RenderingContext context,
                                                PJsonObject params) {
            return Collections.singletonList(new MapServerTileMapReader("t", context, params));
        }
    }

    protected final String layer;
    protected final String path_format;

    protected MapServerTileMapReader(String layer, RenderingContext context, PJsonObject params) {
        super(context, params);
        this.layer = layer;
        PJsonArray maxExtent = params.getJSONArray("maxExtent");
        PJsonArray tileSize = params.getJSONArray("tileSize");
        PJsonArray tileOrigin = params.optJSONArray("tileOrigin");
        String tileOriginCorner = params.optString("tileOriginCorner", "bl");
        final float tileOriginX;
        final float tileOriginY;
        if (tileOrigin == null) {
            tileOriginX = maxExtent.getFloat(0);
            tileOriginY = maxExtent.getFloat(tileOriginCorner.charAt(0) == 't' ? 3 : 1);
        } else {
            tileOriginX = tileOrigin.getFloat(0);
            tileOriginY = tileOrigin.getFloat(1);
        }

        path_format = params.optString("path_format", null);
        tileCacheLayerInfo = new XyzLayerInfo(params.getJSONArray("resolutions"), tileSize.getInt(0), tileSize.getInt(1), maxExtent.getFloat(0), maxExtent.getFloat(1), maxExtent.getFloat(2), maxExtent.getFloat(3),
                params.getString("extension"), tileOriginX, tileOriginY);
    }
    @Override
    protected TileRenderer.Format getFormat() {
        return TileRenderer.Format.BITMAP;
    }
    @Override
    protected void addCommonQueryParams(Map<String, List<String>> result, Transformer transformer, String srs, boolean first) {
        //not much query params for this protocol...
    }
    @Override
    protected URI getTileUri(URI commonUri, Transformer transformer, double minGeoX, double minGeoY, double maxGeoX, double maxGeoY, long w, long h) throws URISyntaxException, UnsupportedEncodingException {
        double targetResolution = (maxGeoX - minGeoX) / w;
        XyzLayerInfo.ResolutionInfo resolution = tileCacheLayerInfo.getNearestResolution(targetResolution);

        int tileX = (int) Math.round((minGeoX - tileCacheLayerInfo.getMinX()) / (resolution.value * w));
        int tileY = (int) Math.round((tileCacheLayerInfo.getMaxY() - minGeoY) / (resolution.value * h));

		StringBuilder query = new StringBuilder();

        if (this.path_format == null) {
            query.append("&tile=").append(tileX);
            query.append('+').append(tileY - 1);
            query.append('+').append(resolution.index);
        } else {
			query.append(this.path_format);

			url_regex_replace("z", query, resolution.index);
			url_regex_replace("x", query, new Integer(tileX));
			url_regex_replace("y", query, new Integer(tileY - 1));
			url_regex_replace("extension", query, tileCacheLayerInfo.getExtension());
        }

        return new URI(commonUri.getScheme(), commonUri.getUserInfo(), commonUri.getHost(), commonUri.getPort(), commonUri.getPath(), (commonUri.getQuery() == null ? query.toString() : commonUri.getQuery() + query), commonUri.getFragment());
    }

    @Override
    public boolean testMerge(MapReader other) {
        return false;
    }
    @Override
    public boolean canMerge(MapReader other) {
        return false;
    }
    @Override
    public String toString() {
        return layer;
    }

    private void url_regex_replace(String needle, StringBuilder haystack, Object replaceValue) {
        Pattern pattern = Pattern.compile("\\$\\{("+needle+"+)\\}");
        Matcher matcher = pattern.matcher(haystack);
        while (matcher.find()) {
            int length = 1;
            if (matcher.groupCount() > 0) {
                length = matcher.group(1).length();
            }
            String value = "";
            if (needle.equals("extension")) {
                value = (String) replaceValue;
            } else {
                value = String.format("%0" + length + "d", replaceValue);
            }
            haystack.replace(matcher.start(), matcher.end(), value);

            matcher = pattern.matcher(haystack);
        }
    }
}

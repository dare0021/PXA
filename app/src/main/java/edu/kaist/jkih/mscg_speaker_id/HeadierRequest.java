package edu.kaist.jkih.mscg_speaker_id;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.JsonRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.Map;

/**
 * Created by jkih on 2017-05-19.
 */

public class HeadierRequest extends JsonObjectRequest
{
    private Map<String, String> header;

    public HeadierRequest(int method, String url, Map<String, String> header, Response.Listener<JSONObject> listener)
    {
        super(method, url, null, listener, new Response.ErrorListener()
        {
            @Override
            public void onErrorResponse(VolleyError error)
            {
                error.printStackTrace();
            }
        });

        this.header = header;
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError
    {
        return header != null ? header : super.getHeaders();
    }

    @Override
    protected Response<JSONObject> parseNetworkResponse(NetworkResponse response)
    {
        if (response.statusCode == 500)
        {
            return null;
        }
        try {
            String jsonString = new String(response.data,
                    HttpHeaderParser.parseCharset(response.headers, PROTOCOL_CHARSET));
            // naked array
            if (!jsonString.startsWith("{"))
            {
                jsonString = "{ \"results\" : " + jsonString + "}";
            }
            return Response.success(new JSONObject(jsonString),
                    HttpHeaderParser.parseCacheHeaders(response));
        } catch (UnsupportedEncodingException e) {
            return Response.error(new ParseError(e));
        } catch (JSONException je) {
            return Response.error(new ParseError(je));
        }
    }
}

package com.zunipe.authonwatch;

import org.json.JSONException;
import org.json.JSONObject;

public class OtpEntry {
    private final String name;
    private final String secret;

    public OtpEntry(String name, String secret) {
        this.name = name;
        this.secret = secret;
    }

    public String getName() {
        return name;
    }

    public String getSecret() {
        return secret;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("name", name);
        object.put("secret", secret);
        return object;
    }

    public static OtpEntry fromJson(JSONObject object) throws JSONException {
        return new OtpEntry(
                object.getString("name"),
                object.getString("secret")
        );
    }
}

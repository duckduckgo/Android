package com.asana.codegen;

import org.json.*;
import io.swagger.codegen.v3.*;

class ExampleUtility {
  public static void tryToSetExample(CodegenParameter p) {
    JSONObject obj = new JSONObject(p.jsonSchema);

    if (obj.has("example")) {
      p.example = obj.get("example").toString();
      return;
    }

    if (!obj.has("schema")) {
      return;
    }

    JSONObject schema = obj.getJSONObject("schema");

    if (!schema.has("example")) {
      return;
    }

    p.example = schema.get("example").toString();
  }
}

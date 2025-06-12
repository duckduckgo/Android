package com.asana.codegen;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.*;
import io.swagger.codegen.v3.*;
import io.swagger.codegen.v3.generators.javascript.*;
import io.swagger.codegen.v3.SupportingFile;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;

public class JavascriptClientCodegenGenerator extends JavaScriptClientCodegen {
    public JavascriptClientCodegenGenerator() {
        super();
        apiDocTemplateFiles.put("code_samples.mustache", ".yaml");
        modelTemplateFiles.remove("model.mustache");
        modelTestTemplateFiles.remove("model_test.mustache");
        modelDocTemplateFiles.remove("model_doc.mustache");
        // Create a definition for collection to be used in pagination
        supportingFiles.add(new SupportingFile("collection.mustache", "src/utils", "collection.js"));
    }

    @Override
    public void processOpts() {
        // custom generators do not set the CodegenConstants
        // Super must be called BEFORE our modification, otherwise the package name
        // somehow ends up wrong
        super.processOpts();
        setProjectName("asana");
    }

    @Override
    public String getModelPropertyNaming() {
        // Javascript tries to use camelCase by default:
        // https://github.com/swagger-api/swagger-codegen-generators/blob/66dcca9d545892e18f982b2cde60621ec6f72bfe/src/main/java/io/swagger/codegen/v3/generators/javascript/JavaScriptClientCodegen.java#L112
        //
        // but we want it to use the OAS schema
        return CodegenConstants.MODEL_PROPERTY_NAMING_TYPE.original.toString();
    }

    @Override
    public String toVarName(String name) {
        // Return the name as defined in the OAS rather than formatting it. EX: instead of returning modified_on_after -> modified_on.after
        return name;
    }

    @Override
    public void setParameterExampleValue(CodegenParameter p) {
        // Our example correction code must execute before super, to ensure that
        // super does its special magic of determining the example type:
        // https://github.com/swagger-api/swagger-codegen-generators/blob/master/src/main/java/io/swagger/codegen/v3/generators/javascript/JavaScriptClientCodegen.java#L714
        ExampleUtility.tryToSetExample(p);

        String example;
        if (p.defaultValue == null) {
            example = p.example;
        } else {
            example = p.defaultValue;
        }

        String type = p.baseType;
        if (type == null) {
            type = p.dataType;
        }

        if ("String".equals(type)) {
            if (example == null) {
                example = p.paramName + "_example";
            }
            // Change opt_fields example from ["param1", "param2"] -> "param1,param2"
            String cleanedInput = example.replace("[", "").replace("]", "").replace("\"", "");
            String[] fields = cleanedInput.split(",");
            String exampleOptFieldString = String.join(",", fields);
            p.example = "\"" + exampleOptFieldString + "\"";
            return;
        } else if ("Date".equals(type)) {
        if (example == null) {
            example = "2013-10-20T19:20:30+01:00";
        }
        // Change from new Date("2012-02-22T02:06:58.158Z") -> "2012-02-22T02:06:58.158Z"
        p.example = "\"" + escapeText(example) + "\"";
        return;
        }

        super.setParameterExampleValue(p);

        // Wrap file request body param example in fs.createReadStream
        if (p.paramName.equalsIgnoreCase("file")) {
            p.example = "fs.createReadStream(" + p.example + ")";
        }

        // Update example for requests that require body
        if (!languageSpecificPrimitives.contains(type)) {
            // type is a model class, e.g. User
            p.example = "{\"data\": {\"<PARAM_1>\": \"<VALUE_1>\", \"<PARAM_2>\": \"<VALUE_2>\",}}";
            p.dataType = "Object";
        }
    }

    @Override
    public CodegenOperation fromOperation(String path, String httpMethod, Operation operation, Map<String, Schema> schemas, OpenAPI openAPI) {
        CodegenOperation op = super.fromOperation(path, httpMethod, operation, schemas, openAPI);
        // Set vendor-extension to be used in template:
        //     x-codegen-isCreateAttachmentForObject
        if(op.operationId.equalsIgnoreCase("createAttachmentForObject")) {
            op.vendorExtensions.put("x-codegen-isCreateAttachmentForObject", true);
        }
        if(op.operationId.equalsIgnoreCase("searchTasksForWorkspace")) {
            op.vendorExtensions.put("x-codegen-isSearchTasksForWorkspace", true);
        }
        // Add a lower case operation ID to link to developer docs
        op.vendorExtensions.put("x-codegen-operationIdLowerCase", op.operationId.toLowerCase());
        // Check if the returnType has "Array" in the name EX: TaskResponseArray is so set isArrayResponse to true
        // This will be used to check if an endpoint should return a collection object or not
        op.vendorExtensions.put("x-codegen-isArrayResponse", op.returnType.contains("Array"));

        // Generate resource instance variable name from the tag. EX: CustomFieldSettings -> customFieldSettingsApiInstance
        String resourceInstanceName = generateResourceInstanceName(operation.getTags().get(0));
        op.vendorExtensions.put("x-codegen-resourceInstanceName", resourceInstanceName);

        // Fix PUT endpoints not adding comma in method request for sample docs. EX: .updateTask(bodytask_gid, opts) -> .updateTask(body, task_gid, opts)
        // Make a copy of parameters to be used in the template (templateParams). This is a hacky work around. We noticed that the below code "p.vendorExtensions.put"
        // does not update the parameter's vendorExtensions for POST and PUT endpoints so to work around this we created a new list that does have those
        // vendorExtensions added
        List<CodegenParameter> templateParams = new ArrayList<CodegenParameter>();
        CodegenParameter lastRequired = null;
        CodegenParameter lastOptional = null;
        for (CodegenParameter p : op.allParams) {
            if (p.required) {
                lastRequired = p;
            } else {
                lastOptional = p;
            }
        }

        // Set vendor-extension to be used in template:
        //     x-codegen-hasMoreRequired
        //     x-codegen-hasMoreOptional
        //     x-codegen-hasRequiredParams
        for (CodegenParameter p : op.allParams) {
            if (p == lastRequired) {
                p.vendorExtensions.put("x-codegen-hasMoreRequired", false);
            } else if (p == lastOptional) {
                p.vendorExtensions.put("x-codegen-hasMoreOptional", false);
            } else {
                p.vendorExtensions.put("x-codegen-hasMoreRequired", true);
                p.vendorExtensions.put("x-codegen-hasMoreOptional", true);
            }
            templateParams.add(p.copy());
        }
        op.vendorExtensions.put("x-codegen-hasRequiredParams", lastRequired != null);
        op.vendorExtensions.put("x-codegen-templateParams",templateParams);

        return op;
    }

    static String generateResourceInstanceName(String inputString) {
        StringBuilder camelCaseString = new StringBuilder();

        // Split input string into words using space as delimiter
        String[] words = inputString.split("\\s+");

        // Capitalize the first letter of each word except the first one
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (i == 0) {
                camelCaseString.append(word.toLowerCase());
            } else {
                camelCaseString.append(word.substring(0, 1).toUpperCase())
                        .append(word.substring(1).toLowerCase());
            }
        }

        // Add "ApiInstance" to the end of the string
        return camelCaseString.toString() + "ApiInstance";
    }
}

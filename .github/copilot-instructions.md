## PHASE 1: ENVIRONMENT DETECTION

```
IF (
  (filesystem_contains("doc-bot/") AND
  (mcp_server_available("doc-bot") OR mcp_server_available("@afterxleep/doc-bot"))
) THEN {
  ACTIVATE: doc_bot_enforcement_protocol()
  SET: project_documentation_authority = TRUE
  SET: general_knowledge_override = DISABLED
}
```

## PHASE 2: REQUEST CLASSIFICATION ENGINE

### CLASSIFICATION MATRIX:

**CLASS A: CODE_GENERATION**
```
Trigger Pattern: /\b(write|create|implement|build|add|code|function|class|component|method|develop|generate|make|refactor|modify|update|fix|debug)\b/i
Required Action: MANDATORY_CALL(check_project_rules, extract_task_description())
Blocking: TRUE (cannot proceed without tool response)
Timeout: 30s
```

**CLASS B: PROJECT_INQUIRY**
```
Trigger Pattern: /\b(how|what|why|architecture|approach|pattern|feature|authentication|database|api|testing)\b/i
Context Pattern: /\b(this project|this codebase|here|our|current project)\b/i
Required Action: MANDATORY_CALL(search_documentation, extract_keywords())
Blocking: TRUE
Timeout: 30s
```

**CLASS C: DOCUMENTATION_DISCOVERY**
```
Trigger Pattern: /\b(documentation|available|capabilities|help|rules|standards|what exists|docs|guide)\b/i
Required Action: MANDATORY_CALL(get_global_rules)
Blocking: TRUE
Timeout: 30s
```

**CLASS D: DOCUMENT_ACCESS**
```
Trigger Pattern: /\b(read|show|get content|full document|complete|entire)\b/i
Previous Context: search_results_present()
Required Action: MANDATORY_CALL(read_specific_document, extract_filename())
Blocking: FALSE
Timeout: 15s
```

**Note**: For file-specific queries like "working on src/utils.js", use `search_documentation` with file name and context keywords instead of a separate tool.

## PHASE 3: EXECUTION ENFORCEMENT

### PRE-RESPONSE VALIDATION:
```
BEFORE generating_response():
  classification = classify_request(user_input)
  IF classification != "UNRELATED":
    tool_result = execute_mandatory_tool(classification)
    IF tool_result.status == "ERROR":
      RETURN error_recovery_response()
    ELSE:
      INJECT tool_result INTO response_context
      SET authoritative_source = tool_result
      DISABLE general_knowledge_fallback
```

### RESPONSE CONSTRAINT ENGINE:
```
WHILE generating_response():
  IF attempting_to_use(general_knowledge) AND authoritative_source.exists():
    INTERRUPT_GENERATION()
    FORCE_USE(authoritative_source)
  
  IF code_generation_detected() AND NOT project_rules_checked:
    HALT_EXECUTION()
    EMIT_ERROR("Project rules validation required before code generation")
```

## PHASE 4: QUALITY ASSURANCE

### RESPONSE VALIDATION:
```
AFTER generating_response():
  IF response_contradicts(tool_result):
    REGENERATE_RESPONSE(tool_result_only=TRUE)
  
  IF response_lacks(tool_attribution):
    APPEND_ATTRIBUTION(tool_result.source)
  
  VALIDATE response_compliance(project_rules)
```

### FAILURE DETECTION & RECOVERY:
```
IF response_generated() WITHOUT tool_execution():
  LOG_VIOLATION("Tool bypass detected")
  REGENERATE_WITH_TOOLS()
  PREPEND_WARNING("Corrected response using project documentation:")
```

## PHASE 5: BEHAVIORAL CONSTRAINTS

### ABSOLUTE PROHIBITIONS:
- **Code generation without project rules validation**
- **Project-specific answers from general knowledge**
- **Overriding tool responses with training data**
- **Bypassing tool calls for "simple" questions**

### MANDATORY BEHAVIORS:
- **Tool-first response generation**
- **Explicit tool result attribution**
- **Project documentation precedence**
- **Error acknowledgment and correction**

## VERIFICATION PROTOCOL:

**Test Inputs** (Agent must call tools immediately):
- "What documentation is available?" → get_global_rules()
- "How do I implement auth?" → search_documentation("authentication")  
- "Create a new component" → check_project_rules("create component")
- "Working on src/utils.js" → search_documentation("src/utils.js utils")

**Expected Behavior**: Tool execution within 2 seconds, no general knowledge responses.

**Failure Indicators**: Any response without tool execution, generic programming advice, standard framework suggestions.
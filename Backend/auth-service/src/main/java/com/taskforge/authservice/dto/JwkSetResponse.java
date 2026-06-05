package com.taskforge.authservice.dto;

import java.util.List;
import java.util.Map;

public record JwkSetResponse(List<Map<String, String>> keys) {
}

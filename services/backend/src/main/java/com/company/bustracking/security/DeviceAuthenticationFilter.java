package com.company.bustracking.security;

import com.company.bustracking.domain.Device;
import com.company.bustracking.repository.DeviceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class DeviceAuthenticationFilter extends OncePerRequestFilter {
    public static final String DEVICE_ATTRIBUTE = DeviceAuthenticationFilter.class.getName() + ".device";
    public static final String HARDWARE_SERIAL_HEADER = "X-Device-Hardware-Serial";
    private static final Pattern HARDWARE_SERIAL_PATTERN =
            Pattern.compile("[A-Z0-9][A-Z0-9._-]{0,99}");

    private final DeviceRepository devices;
    private final ObjectMapper objectMapper;
    private final byte[] fleetApiKey;

    public DeviceAuthenticationFilter(
            DeviceRepository devices,
            ObjectMapper objectMapper,
            @Value("${app.device.api-key}") String fleetApiKey) {
        this.devices = devices;
        this.objectMapper = objectMapper;
        this.fleetApiKey = fleetApiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/device/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod())
                && request.getContentLengthLong() > 1024L * 1024L) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            unauthorized(response, "Missing device API key");
            return;
        }

        String rawKey = authorization.substring(7).trim();
        if (!MessageDigest.isEqual(
                fleetApiKey,
                rawKey.getBytes(StandardCharsets.UTF_8))) {
            unauthorized(response, "Invalid device API key");
            return;
        }

        String hardwareSerial = request.getHeader(HARDWARE_SERIAL_HEADER);
        if (hardwareSerial == null || hardwareSerial.isBlank()) {
            unauthorized(response, "Missing device hardware serial");
            return;
        }
        hardwareSerial = hardwareSerial.trim().toUpperCase(Locale.ROOT);
        if (!HARDWARE_SERIAL_PATTERN.matcher(hardwareSerial).matches()) {
            unauthorized(response, "Invalid device hardware serial");
            return;
        }

        Device device = devices.findByHardwareSerialAndActiveTrue(hardwareSerial).orElse(null);
        if (device == null || !device.getBus().isActive()) {
            unauthorized(response, "Unknown or inactive device");
            return;
        }

        request.setAttribute(DEVICE_ATTRIBUTE, device);
        filterChain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of("message", message));
    }

}

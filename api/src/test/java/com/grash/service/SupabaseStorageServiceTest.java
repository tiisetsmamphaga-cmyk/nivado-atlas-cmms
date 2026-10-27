package com.grash.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grash.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SupabaseStorageServiceTest {
    private HttpClient client;
    private SupabaseStorageService service;

    @BeforeEach
    void setup() {
        client = mock(HttpClient.class);
        service = new SupabaseStorageService(new ObjectMapper(), mock(CacheService.class),
                "https://example.supabase.co", "sb_secret_test", "atlas-files");
        ReflectionTestUtils.setField(service, "client", client);
    }

    @SuppressWarnings("unchecked")
    private void reply(int status, String body) throws Exception {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    @Test
    void signsPrivateObjectWithEncodedPath() throws Exception {
        reply(200, "{\"signedURL\":\"/object/sign/atlas-files/a%20b.txt?token=test\"}");
        assertEquals("https://example.supabase.co/storage/v1/object/sign/atlas-files/a%20b.txt?token=test",
                service.generateSignedUrl("a b.txt", 10));
        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("/storage/v1/object/sign/atlas-files/a%20b.txt", request.getValue().uri().getRawPath());
        assertEquals("sb_secret_test", request.getValue().headers().firstValue("apikey").orElseThrow());
        assertTrue(request.getValue().headers().firstValue("Authorization").isEmpty());
    }

    @Test
    void missingObjectIsFalseButPermissionFailureIsReported() throws Exception {
        reply(404, "{}");
        assertFalse(service.exists("missing.txt"));
        reply(400, "{\"statusCode\":\"404\",\"code\":\"NoSuchKey\"}");
        assertFalse(service.exists("missing.txt"));
        reply(403, "secret upstream error");
        CustomException error = assertThrows(CustomException.class, () -> service.exists("hidden.txt"));
        assertFalse(error.getMessage().contains("secret upstream"));
    }

    @Test
    void rejectsTraversalAndUntrustedSignedUrl() throws Exception {
        assertThrows(CustomException.class, () -> service.download("../other.txt"));
        verifyNoInteractions(client);
        reply(200, "{\"signedURL\":\"https://untrusted.example/file\"}");
        assertThrows(CustomException.class, () -> service.generateSignedUrl("file.txt", 10));
    }
}

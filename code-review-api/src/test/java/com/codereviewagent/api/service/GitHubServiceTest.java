package com.codereviewagent.api.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class GitHubServiceTest {
    private GitHubService service() { return new GitHubService(new ObjectMapper(), "", 100000, 10, 1000, 10000); }

    @Test void acceptsOnlyCanonicalPublicRepositoryUrl() {
        assertEquals("https://github.com/acme/project", service().parse("https://github.com/acme/project.git").url());
        for (String url : new String[] {"http://github.com/acme/project", "https://github.com.evil.test/acme/project",
                "https://user:pass@github.com/acme/project", "https://github.com/acme/project/tree/main",
                "https://github.com/acme/project?x=1", "https://127.0.0.1/acme/project"}) {
            assertThrows(ResponseStatusException.class, () -> service().parse(url), url);
        }
    }

    @Test void recognizesSupportedLanguages() {
        assertEquals("JAVA", GitHubService.language("src/App.java"));
        assertEquals("JSX", GitHubService.language("src/App.jsx"));
        assertEquals("PYTHON", GitHubService.language("app.py"));
        assertNull(GitHubService.language("logo.png"));
    }

    @Test void readsSourceAndReportsExcludedFiles() throws Exception {
        byte[] archive = zip(new String[][] {{"root/src/App.java", "class App {}"},
                {"root/node_modules/a.js", "console.log(1)"}, {"root/readme.md", "hello"}});
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive))) {
            var result = service().readArchive(input);
            assertEquals(1, result.files().size());
            assertEquals("src/App.java", result.files().get(0).path());
            assertEquals(2, result.skippedFiles());
            assertFalse(result.warnings().isEmpty());
        }
    }

    @Test void rejectsArchiveWithNoSupportedSource() throws Exception {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip(new String[][] {{"root/a.png", "x"}})))) {
            assertThrows(IOException.class, () -> service().readArchive(input));
        }
    }

    @Test void rejectsOversizedSource() throws Exception {
        GitHubService limited = new GitHubService(new ObjectMapper(), "", 100000, 10, 5, 10000);
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip(new String[][] {{"root/App.java", "class App {}"}})))) {
            assertThrows(IOException.class, () -> limited.readArchive(input));
        }
    }

    @Test void inspectsPublicRepositoryAndResolvesCommit() throws Exception {
        HttpClient client = mock(HttpClient.class);
        doAnswer(inv -> {
            HttpRequest request = inv.getArgument(0);
            String path = request.uri().toString();
            if (path.endsWith("/branches?per_page=100")) return json(200, "[{\"name\":\"main\"}]");
            if (path.endsWith("/commits/main")) return json(200, "{\"sha\":\"" + "a".repeat(40) + "\"}");
            return json(200, "{\"private\":false,\"default_branch\":\"main\"}");
        }).when(client).send(any(), any());
        GitHubService service = new GitHubService(new ObjectMapper(), "", 100000, 10, 1000, 10000, client);
        var repo = service.parse("https://github.com/acme/sample");
        assertEquals("main", service.inspect(repo).defaultBranch());
        assertEquals(List.of("main"), service.inspect(repo).branches());
        assertEquals("a".repeat(40), service.resolveCommit(repo, "main"));
    }

    @Test void rejectsPrivateRepositoryAndMissingRef() throws Exception {
        HttpClient client = mock(HttpClient.class);
        doAnswer(inv -> json(200, "{\"private\":true,\"default_branch\":\"main\"}"))
                .when(client).send(any(), any());
        GitHubService service = new GitHubService(new ObjectMapper(), "", 100000, 10, 1000, 10000, client);
        assertThrows(ResponseStatusException.class, () -> service.inspect(new GitHubService.Repo("acme", "private")));
        assertThrows(ResponseStatusException.class, () -> service.resolveCommit(new GitHubService.Repo("acme", "private"), "../bad"));
    }

    @Test void downloadsArchiveOnlyFromCodeloadGitHub() throws Exception {
        HttpClient client = mock(HttpClient.class);
        byte[] zipBytes = zip(new String[][] {{"root/src/App.java", "class App {}"}});
        doAnswer(inv -> {
            HttpRequest request = inv.getArgument(0);
            if (request.uri().getHost().equals("api.github.com")) {
                HttpResponse<Void> redirect = mock(HttpResponse.class);
                when(redirect.statusCode()).thenReturn(302);
                when(redirect.headers()).thenReturn(HttpHeaders.of(Map.of("Location", List.of("https://codeload.github.com/acme/sample/zip/abc")), (a, b) -> true));
                return redirect;
            }
            HttpResponse<java.io.InputStream> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(new ByteArrayInputStream(zipBytes));
            return response;
        }).when(client).send(any(), any());
        GitHubService service = new GitHubService(new ObjectMapper(), "", 100000, 10, 1000, 10000, client);
        assertEquals(1, service.archive(new GitHubService.Repo("acme", "sample"), "a".repeat(40)).files().size());
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> json(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    private static byte[] zip(String[][] files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (String[] file : files) {
                out.putNextEntry(new ZipEntry(file[0]));
                out.write(file[1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}

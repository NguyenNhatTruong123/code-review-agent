package com.codereviewagent.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Loads public GitHub metadata and bounded source archives for a pinned commit. */
@Service
public class GitHubService {
    /**
     * Parsed repository identity accepted by the GitHub API.
     *
     * @param owner GitHub owner or organization name
     * @param name repository name without an optional {@code .git} suffix
     */
    public record Repo(String owner, String name) {
        /**
         * Returns the canonical HTTPS repository URL.
         *
         * @return canonical repository URL
         */
        public String url() {
            return "https://github.com/" + owner + "/" + name;
        }
    }

    /**
     * Repository metadata used to choose a branch before review creation.
     *
     * @param repositoryUrl canonical repository URL
     * @param defaultBranch repository default branch
     * @param branches available branch names
     */
    public record RepoInfo(String repositoryUrl, String defaultBranch, List<String> branches) {}

    /**
     * Source file with a normalized language identifier.
     *
     * @param path repository-relative source path
     * @param language normalized language identifier
     * @param code UTF-8 source text
     */
    public record SourceFile(String path, String language, String code) {}

    /** Metadata for a reviewable source file, without loading its source content. */
    public record SourceFileInfo(String path, String language) {}

    /**
     * Archive result including files deliberately skipped by safety or size limits.
     *
     * @param files accepted source files
     * @param skippedFiles count of excluded or invalid files
     * @param warnings bounded warning messages for skipped content
     */
    public record SourceArchive(List<SourceFile> files, int skippedFiles, List<String> warnings) {}

    private static final Set<String> SKIP_DIRS =
            Set.of(
                    "node_modules",
                    "vendor",
                    "dist",
                    "build",
                    "target",
                    ".git",
                    "coverage",
                    "generated",
                    ".next",
                    "out");
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String token;
    private final long maxArchiveBytes;
    private final int maxFiles;
    private final int maxFileBytes;
    private final long maxTotalSourceBytes;

    /**
     * Creates a GitHub client with configured archive and source-size limits.
     *
     * @param mapper JSON parser for GitHub responses
     * @param token optional GitHub API credential
     * @param maxArchiveBytes compressed archive limit
     * @param maxFiles maximum accepted source file count
     * @param maxFileBytes maximum accepted file size
     * @param maxTotalSourceBytes maximum accepted decompressed source size
     */
    @Autowired
    public GitHubService(
            ObjectMapper mapper,
            @Value("${app.github-token:}") String token,
            @Value("${app.max-archive-bytes}") long maxArchiveBytes,
            @Value("${app.max-files}") int maxFiles,
            @Value("${app.max-file-bytes}") int maxFileBytes,
            @Value("${app.max-total-source-bytes}") long maxTotalSourceBytes) {
        this(
                mapper,
                token,
                maxArchiveBytes,
                maxFiles,
                maxFileBytes,
                maxTotalSourceBytes,
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .connectTimeout(Duration.ofSeconds(10))
                        .build());
    }

    GitHubService(
            ObjectMapper mapper,
            String token,
            long maxArchiveBytes,
            int maxFiles,
            int maxFileBytes,
            long maxTotalSourceBytes,
            HttpClient client) {
        this.mapper = mapper;
        this.token = token;
        this.maxArchiveBytes = maxArchiveBytes;
        this.maxFiles = maxFiles;
        this.maxFileBytes = maxFileBytes;
        this.maxTotalSourceBytes = maxTotalSourceBytes;
        this.client = client;
    }

    /**
     * Validates and parses an HTTPS public GitHub repository URL.
     *
     * @param url user-supplied repository URL
     * @return parsed repository owner and name
     * @throws ResponseStatusException with HTTP 400 when the URL is not an allowed repository URL
     */
    public Repo parse(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"github.com".equalsIgnoreCase(uri.getHost())
                    || uri.getRawUserInfo() != null
                    || uri.getPort() != -1
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || uri.getRawPath() == null) {
                throw new IllegalArgumentException();
            }
            String[] parts = uri.getRawPath().split("/", -1);
            if (parts.length != 3
                    || !parts[1].matches("[A-Za-z0-9-]{1,39}")
                    || !parts[2].matches("[A-Za-z0-9._-]{1,100}")) {
                throw new IllegalArgumentException();
            }
            String name =
                    parts[2].endsWith(".git")
                            ? parts[2].substring(0, parts[2].length() - 4)
                            : parts[2];
            if (name.isBlank() || name.equals(".") || name.equals("..")) {
                throw new IllegalArgumentException();
            }
            return new Repo(parts[1], name);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Enter a public GitHub repository URL", e);
        }
    }

    /**
     * Retrieves public repository metadata and available branches.
     *
     * @param repo parsed repository identity
     * @return default branch and available branches
     * @throws ResponseStatusException when GitHub rejects, cannot find, or rate-limits the request
     */
    public RepoInfo inspect(Repo repo) {
        JsonNode details =
                getJson("https://api.github.com/repos/" + repo.owner() + "/" + repo.name());
        if (details.path("private").asBoolean(true)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repository is not public");
        }
        String defaultBranch = details.path("default_branch").asText();
        if (defaultBranch.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Repository has no default branch");
        }
        JsonNode items =
                getJson(
                        "https://api.github.com/repos/"
                                + repo.owner()
                                + "/"
                                + repo.name()
                                + "/branches?per_page=100");
        List<String> branches = new ArrayList<>();
        if (items.isArray()) {
            for (JsonNode item : items) {
                branches.add(item.path("name").asText());
            }
        }
        return new RepoInfo(repo.url(), defaultBranch, branches);
    }

    /**
     * Resolves a branch or commit reference to a 40-character commit SHA.
     *
     * @param repo parsed repository identity
     * @param ref branch or commit reference
     * @return immutable 40-character commit SHA
     * @throws ResponseStatusException when the ref is invalid or GitHub returns an invalid response
     */
    public String resolveCommit(Repo repo, String ref) {
        if (ref == null
                || ref.isBlank()
                || ref.length() > 200
                || !ref.matches("[A-Za-z0-9._/-]+")
                || ref.startsWith("/")
                || ref.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid branch or commit");
        }
        String encoded = URLEncoder.encode(ref, StandardCharsets.UTF_8);
        JsonNode commit =
                getJson(
                        "https://api.github.com/repos/"
                                + repo.owner()
                                + "/"
                                + repo.name()
                                + "/commits/"
                                + encoded);
        String sha = commit.path("sha").asText();
        if (!sha.matches("[a-fA-F0-9]{40}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "GitHub returned an invalid commit");
        }
        return sha;
    }

    /**
     * Lists reviewable source files at a ref through GitHub's tree API. Source contents are not
     * downloaded until a review is submitted.
     */
    public List<SourceFileInfo> listSourceFiles(Repo repo, String ref) {
        String sha = resolveCommit(repo, ref);
        JsonNode response =
                getJson(
                        "https://api.github.com/repos/"
                                + repo.owner()
                                + "/"
                                + repo.name()
                                + "/git/trees/"
                                + sha
                                + "?recursive=1");
        if (response.path("truncated").asBoolean(false)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Repository file list is too large to inspect");
        }

        List<SourceFileInfo> files = new ArrayList<>();
        for (JsonNode entry : response.path("tree")) {
            String path = entry.path("path").asText();
            String language = language(path);
            if ("blob".equals(entry.path("type").asText())
                    && safePath(path)
                    && language != null
                    && !excluded(path)) {
                files.add(new SourceFileInfo(path, language));
            }
        }
        if (files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No supported source files found");
        }
        return List.copyOf(files);
    }

    /** Loads only explicitly selected source files from a pinned commit. */
    public SourceArchive selectedFiles(Repo repo, String sha, List<String> selectedPaths) {
        if (!sha.matches("[a-fA-F0-9]{40}")) {
            throw new IllegalArgumentException("Invalid commit SHA");
        }
        LinkedHashSet<String> paths = new LinkedHashSet<>(selectedPaths);
        if (paths.isEmpty() || paths.size() > maxFiles) {
            throw new IllegalArgumentException("Invalid selected file count");
        }

        List<SourceFile> files = new ArrayList<>();
        long total = 0;
        for (String path : paths) {
            String language = language(path);
            if (!safePath(path) || language == null || excluded(path)) {
                throw new IllegalArgumentException("Invalid selected source file");
            }
            String encodedPath =
                    String.join(
                            "/",
                            java.util.Arrays.stream(path.split("/"))
                                    .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8))
                                    .toList());
            JsonNode item =
                    getJson(
                            "https://api.github.com/repos/"
                                    + repo.owner()
                                    + "/"
                                    + repo.name()
                                    + "/contents/"
                                    + encodedPath
                                    + "?ref="
                                    + sha);
            if (!"file".equals(item.path("type").asText()) || !item.hasNonNull("content")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected file is unavailable");
            }
            byte[] content;
            try {
                content = Base64.getMimeDecoder().decode(item.path("content").asText());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub returned invalid source content");
            }
            if (content.length == 0 || content.length > maxFileBytes || hasNul(content)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected file cannot be reviewed");
            }
            total += content.length;
            if (total > maxTotalSourceBytes) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected files exceed source size limit");
            }
            try {
                String code =
                        StandardCharsets.UTF_8
                                .newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(content))
                                .toString();
                files.add(new SourceFile(path, language, code));
            } catch (CharacterCodingException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected file is not UTF-8 text");
            }
        }
        return new SourceArchive(List.copyOf(files), 0, List.of());
    }

    /**
     * Downloads and bounds a commit archive, rejecting unsafe redirects and files.
     *
     * @param repo parsed repository identity
     * @param sha immutable 40-character commit SHA
     * @return accepted source files and skip warnings
     * @throws IOException when the archive is invalid, too large, unsafe, or unreadable
     * @throws InterruptedException when the archive request is interrupted
     */
    public SourceArchive archive(Repo repo, String sha) throws IOException, InterruptedException {
        if (!sha.matches("[a-fA-F0-9]{40}")) {
            throw new IllegalArgumentException("Invalid commit SHA");
        }
        URI uri =
                URI.create(
                        "https://api.github.com/repos/"
                                + repo.owner()
                                + "/"
                                + repo.name()
                                + "/zipball/"
                                + sha);
        // GitHub's zipball endpoint redirects to codeload.github.com; allow only that fixed GitHub
        // host.
        HttpResponse<Void> redirect =
                client.send(request(uri).GET().build(), HttpResponse.BodyHandlers.discarding());
        if (redirect.statusCode() != 302 && redirect.statusCode() != 301) {
            throw new IOException("GitHub archive returned HTTP " + redirect.statusCode());
        }
        URI archiveUri = uri.resolve(redirect.headers().firstValue("Location").orElse(""));
        if (!"https".equals(archiveUri.getScheme())
                || !"codeload.github.com".equals(archiveUri.getHost())
                || archiveUri.getPort() != -1
                || archiveUri.getRawUserInfo() != null) {
            throw new IOException("Invalid GitHub archive redirect");
        }
        HttpResponse<InputStream> response =
                client.send(
                        request(archiveUri).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("GitHub archive returned HTTP " + response.statusCode());
        }
        try (InputStream raw = response.body();
                ZipInputStream zip =
                        new ZipInputStream(new LimitedInputStream(raw, maxArchiveBytes))) {
            return readArchive(zip);
        }
    }

    SourceArchive readArchive(ZipInputStream zip) throws IOException {
        // Read every entry to keep the archive stream aligned, but retain only reviewable source
        // files.
        List<SourceFile> files = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int skipped = 0;
        int entries = 0;
        long total = 0;
        ZipEntry entry;

        while ((entry = zip.getNextEntry()) != null) {
            if (++entries > Math.max(maxFiles * 10, 1000)) {
                throw new IOException("Repository has too many archive entries");
            }
            if (entry.isDirectory()) {
                continue;
            }

            String path = entry.getName();
            int slash = path.indexOf('/');
            if (slash < 0) {
                skipped++;
                continue;
            }
            path = path.substring(slash + 1);
            if (!safePath(path)) {
                skipped++;
                continue;
            }

            String language = language(path);
            boolean excluded = language == null || excluded(path);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            boolean tooLarge = false;

            while ((read = zip.read(buffer)) != -1) {
                total += read;
                if (total > maxTotalSourceBytes * 4) {
                    throw new IOException("Repository exceeds decompressed size limit");
                }
                if (!excluded && !tooLarge) {
                    if (bytes.size() + read > maxFileBytes) {
                        tooLarge = true;
                    } else {
                        bytes.write(buffer, 0, read);
                    }
                }
            }

            byte[] content = bytes.toByteArray();
            if (excluded || tooLarge || content.length == 0 || hasNul(content)) {
                skipped++;
                if (tooLarge && warnings.size() < 10) {
                    warnings.add("Skipped oversized file: " + path);
                }
                continue;
            }

            if (files.size() >= maxFiles || total > maxTotalSourceBytes) {
                throw new IOException("Repository exceeds source file limit");
            }

            try {
                String code =
                        StandardCharsets.UTF_8
                                .newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(content))
                                .toString();
                files.add(new SourceFile(path, language, code));
            } catch (CharacterCodingException e) {
                skipped++;
                if (warnings.size() < 10) {
                    warnings.add("Skipped non-UTF-8 file: " + path);
                }
            }
        }

        if (files.isEmpty()) {
            throw new IOException("No supported source files found");
        }
        if (skipped > 0) {
            warnings.add("Skipped " + skipped + " unsupported or excluded files");
        }
        return new SourceArchive(List.copyOf(files), skipped, List.copyOf(warnings));
    }

    private JsonNode getJson(String url) {
        try {
            HttpResponse<String> response =
                    client.send(
                            request(URI.create(url)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Repository or ref not found or not public");
            }
            if (response.statusCode() == 403 || response.statusCode() == 429) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "GitHub rate limit reached; try again later");
            }
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "GitHub returned HTTP " + response.statusCode());
            }
            if (response.body().length() > 2000000) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "GitHub response too large");
            }
            return mapper.readTree(response.body());
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Could not read GitHub response", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "GitHub request interrupted", e);
        }
    }

    private HttpRequest.Builder request(URI uri) {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(30))
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "code-review-agent");
        if ("api.github.com".equals(uri.getHost()) && token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    /**
     * Maps a supported source filename extension to the API language identifier.
     *
     * @param path source filename or path
     * @return normalized language identifier, or {@code null} when unsupported
     */
    public static String language(String path) {
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".java")) {
            return "JAVA";
        }
        if (lower.endsWith(".jsx")) {
            return "JSX";
        }
        if (lower.endsWith(".tsx")) {
            return "TSX";
        }
        if (lower.endsWith(".js") || lower.endsWith(".mjs") || lower.endsWith(".cjs")) {
            return "JAVASCRIPT";
        }
        if (lower.endsWith(".ts")) {
            return "TYPESCRIPT";
        }
        if (lower.endsWith(".py")) {
            return "PYTHON";
        }
        if (lower.endsWith(".go")) {
            return "GO";
        }
        if (lower.endsWith(".cs")) {
            return "CSHARP";
        }
        if (lower.endsWith(".rb")) {
            return "RUBY";
        }
        if (lower.endsWith(".php")) {
            return "PHP";
        }
        if (lower.endsWith(".rs")) {
            return "RUST";
        }
        if (lower.endsWith(".kt") || lower.endsWith(".kts")) {
            return "KOTLIN";
        }
        if (lower.endsWith(".swift")) {
            return "SWIFT";
        }
        if (lower.endsWith(".c") || lower.endsWith(".h")) {
            return "C";
        }
        if (lower.endsWith(".cpp") || lower.endsWith(".cc") || lower.endsWith(".hpp")) {
            return "CPP";
        }
        if (lower.endsWith(".vue")) {
            return "VUE";
        }
        if (lower.endsWith(".sql")) {
            return "SQL";
        }
        return null;
    }

    private static boolean safePath(String path) {
        return !path.isBlank()
                && !path.startsWith("/")
                && !path.contains("\\")
                && !path.contains("../")
                && !path.contains("/..")
                && !path.contains("//");
    }

    private static boolean excluded(String path) {
        for (String segment : path.split("/")) {
            if (SKIP_DIRS.contains(segment.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNul(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    private static class LimitedInputStream extends FilterInputStream {
        private long remaining;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            remaining = limit;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1 && --remaining < 0) {
                throw new IOException("Archive too large");
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0 && (remaining -= n) < 0) {
                throw new IOException("Archive too large");
            }
            return n;
        }
    }
}

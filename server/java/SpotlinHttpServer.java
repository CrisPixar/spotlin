package com.spotlin.messenger.server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Spotlin Server in Java
 * Real standalone backend server providing REST API, real image/media compression,
 * user authentication, role-based access control, 1-on-1 chats, and school calendar management.
 */
public class SpotlinHttpServer {

    private static final int DEFAULT_PORT = 8080;
    private static final String UPLOAD_DIR = "server/uploads";
    private static final String THUMB_DIR = "server/uploads/thumbnails";

    // In-memory persistent data structures
    public static class User {
        public String id;
        public String username;
        public String passwordHash;
        public String bio = "";
        public String avatarUrl = "";
        public String role = "user";
        public boolean isCreator = false;
        public String customBadge = "";
        public boolean canEditCalendar = false;
        public boolean canManageChat = false;

        public User(String id, String username, String passwordHash) {
            this.id = id;
            this.username = username;
            this.passwordHash = passwordHash;
        }
    }

    public static class Chat {
        public String id;
        public String type; // direct or group
        public String title;
        public String createdBy;
        public List<String> memberIds = new ArrayList<>();
        public String pinnedMessageId;
        public long createdAt;
    }

    public static class Message {
        public String id;
        public String chatId;
        public String senderId;
        public String ciphertext;
        public String iv;
        public String mediaUrl;
        public String mediaType;
        public boolean isPinned = false;
        public boolean isEdited = false;
        public long createdAt;
    }

    public static class SchoolLesson {
        public int number;
        public String subject;
        public String timeRange;
        public String homework;
        public String updatedBy;

        public SchoolLesson(int number, String subject, String timeRange, String homework) {
            this.number = number;
            this.subject = subject;
            this.timeRange = timeRange;
            this.homework = homework;
        }
    }

    private static final Map<String, User> users = new ConcurrentHashMap<>();
    private static final Map<String, Chat> chats = new ConcurrentHashMap<>();
    private static final Map<String, List<Message>> messagesByChat = new ConcurrentHashMap<>();
    private static final List<SchoolLesson> schedule = Collections.synchronizedList(new ArrayList<>());

    static {
        // Initialize upload directories
        new File(UPLOAD_DIR).mkdirs();
        new File(THUMB_DIR).mkdirs();

        // Seed Creator / Owner: CrisPixar
        String ownerId = "owner_cris_pixar";
        User owner = new User(ownerId, "CrisPixar", hashPassword("spotlin_creator_2026"));
        owner.bio = "Создатель и разработчик школьного мессенджера Spotlin";
        owner.avatarUrl = "/uploads/creator.png";
        owner.role = "owner";
        owner.isCreator = true;
        owner.customBadge = "Создатель";
        owner.canEditCalendar = true;
        owner.canManageChat = true;
        users.put(ownerId, owner);

        // Seed initial school schedule
        schedule.add(new SchoolLesson(1, "Алгебра", "08:30 - 09:15", "Параграф 14, № 14.5, 14.8"));
        schedule.add(new SchoolLesson(2, "Геометрия", "09:25 - 10:10", "Теорема о трех перпендикулярах"));
        schedule.add(new SchoolLesson(3, "Физика", "10:30 - 11:15", "Доклад: Законы термодинамики"));
        schedule.add(new SchoolLesson(4, "Русский язык", "11:35 - 12:20", "Упражнение 218"));
        schedule.add(new SchoolLesson(5, "Литература", "12:35 - 13:20", "Война и мир (тома 1-2)"));
        schedule.add(new SchoolLesson(6, "Информатика", "13:30 - 14:15", "Spotlin Java & Python Engine"));
    }

    public static void main(String[] args) throws Exception {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (Exception ignored) {}
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool());

        server.createContext("/api/auth/register", new RegisterHandler());
        server.createContext("/api/auth/login", new LoginHandler());
        server.createContext("/api/users", new UsersHandler());
        server.createContext("/api/admin/delegate", new AdminDelegateHandler());
        server.createContext("/api/chats", new ChatsHandler());
        server.createContext("/api/calendar", new CalendarHandler());
        server.createContext("/api/upload", new UploadHandler());
        server.createContext("/uploads", new MediaStreamHandler());

        System.out.println("Spotlin Java Server running on port " + port);
        server.start();
    }

    // --- Authentication & Handlers ---

    private static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(200, -1); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}"); return; }

            String body = readBody(ex);
            String username = extractJsonString(body, "username");
            String password = extractJsonString(body, "password");
            String bio = extractJsonString(body, "bio");

            if (username.length() < 3 || password.length() < 4) {
                sendJson(ex, 400, "{\"error\":\"Invalid username or password length\"}");
                return;
            }

            for (User u : users.values()) {
                if (u.username.equalsIgnoreCase(username)) {
                    sendJson(ex, 409, "{\"error\":\"Username already taken\"}");
                    return;
                }
            }

            String userId = "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            boolean isCreator = "crispixar".equalsIgnoreCase(username);
            User user = new User(userId, username, hashPassword(password));
            user.bio = bio;
            user.isCreator = isCreator;
            user.role = isCreator ? "owner" : "user";
            user.customBadge = isCreator ? "Создатель" : "";
            user.canEditCalendar = isCreator;
            user.canManageChat = isCreator;
            users.put(userId, user);

            String response = String.format("{\"token\":\"%s\",\"user\":%s}", userId, serializeUser(user));
            sendJson(ex, 200, response);
        }
    }

    private static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(200, -1); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { sendJson(ex, 405, "{\"error\":\"Method Not Allowed\"}"); return; }

            String body = readBody(ex);
            String username = extractJsonString(body, "username");
            String password = extractJsonString(body, "password");
            String pwHash = hashPassword(password);

            for (User u : users.values()) {
                if (u.username.equalsIgnoreCase(username) && u.passwordHash.equals(pwHash)) {
                    String response = String.format("{\"token\":\"%s\",\"user\":%s}", u.id, serializeUser(u));
                    sendJson(ex, 200, response);
                    return;
                }
            }
            sendJson(ex, 401, "{\"error\":\"Invalid credentials\"}");
        }
    }

    private static class UsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(200, -1); return; }

            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (User u : users.values()) {
                if (!first) sb.append(",");
                sb.append(serializeUser(u));
                first = false;
            }
            sb.append("]");
            sendJson(ex, 200, sb.toString());
        }
    }

    private static class AdminDelegateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(200, -1); return; }

            User caller = authenticate(ex);
            if (caller == null || !caller.isCreator) {
                sendJson(ex, 403, "{\"error\":\"Forbidden: Owner access required\"}");
                return;
            }

            String body = readBody(ex);
            String targetId = extractJsonString(body, "user_id");
            String badge = extractJsonString(body, "custom_badge");
            boolean canEdit = body.contains("\"can_edit_calendar\":true");
            boolean canManage = body.contains("\"can_manage_chat\":true");

            User target = users.get(targetId);
            if (target != null) {
                target.customBadge = badge;
                target.canEditCalendar = canEdit;
                target.canManageChat = canManage;
                sendJson(ex, 200, serializeUser(target));
            } else {
                sendJson(ex, 404, "{\"error\":\"User not found\"}");
            }
        }
    }

    private static class ChatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(200, -1); return; }

            User caller = authenticate(ex);
            if (caller == null) { sendJson(ex, 401, "{\"error\":\"Unauthorized\"}"); return; }

            String method = ex.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (Chat c : chats.values()) {
                    if (caller.isCreator || c.memberIds.contains(caller.id)) {
                        if (!first) sb.append(",");
                        sb.append(serializeChat(c));
                        first = false;
                    }
                }
                sb.append("]");
                sendJson(ex, 200, sb.toString());
            } else if ("POST".equalsIgnoreCase(method)) {
                String body = readBody(ex);
                String type = extractJsonString(body, "type");
                if (type.isEmpty()) type = "direct";
                String targetUserId = extractJsonString(body, "target_user_id");

                if (!"direct".equalsIgnoreCase(type) && !caller.isCreator) {
                    sendJson(ex, 403, "{\"error\":\"Regular users are only allowed direct 1-on-1 chats\"}");
                    return;
                }

                String chatId = "chat_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                Chat chat = new Chat();
                chat.id = chatId;
                chat.type = type;
                chat.title = extractJsonString(body, "title");
                if (chat.title.isEmpty()) chat.title = "Диалог";
                chat.createdBy = caller.id;
                chat.createdAt = System.currentTimeMillis();
                chat.memberIds.add(caller.id);
                if (!targetUserId.isEmpty()) chat.memberIds.add(targetUserId);

                chats.put(chatId, chat);
                messagesByChat.put(chatId, Collections.synchronizedList(new ArrayList<>()));

                sendJson(ex, 200, serializeChat(chat));
            }
        }
    }

    private static class CalendarHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(200, -1); return; }

            String method = ex.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (SchoolLesson l : schedule) {
                    if (!first) sb.append(",");
                    sb.append(String.format("{\"lesson_number\":%d,\"subject\":\"%s\",\"time_range\":\"%s\",\"homework\":\"%s\"}",
                            l.number, l.subject, l.timeRange, l.homework));
                    first = false;
                }
                sb.append("]");
                sendJson(ex, 200, sb.toString());
            } else if ("PUT".equalsIgnoreCase(method)) {
                User caller = authenticate(ex);
                if (caller == null || (!caller.isCreator && !caller.canEditCalendar)) {
                    sendJson(ex, 403, "{\"error\":\"Forbidden: Requires calendar editing permissions\"}");
                    return;
                }
                String body = readBody(ex);
                String homework = extractJsonString(body, "homework");
                String path = ex.getRequestURI().getPath();
                String[] parts = path.split("/");
                int lessonId = Integer.parseInt(parts[parts.length - 1]);

                for (SchoolLesson l : schedule) {
                    if (l.number == lessonId) {
                        l.homework = homework;
                        l.updatedBy = caller.username;
                        sendJson(ex, 200, String.format("{\"lesson_number\":%d,\"subject\":\"%s\",\"homework\":\"%s\"}", l.number, l.subject, l.homework));
                        return;
                    }
                }
                sendJson(ex, 404, "{\"error\":\"Lesson not found\"}");
            }
        }
    }

    // Media compression & upload
    private static class UploadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ex.sendResponseHeaders(200, -1); return; }

            String fileId = UUID.randomUUID().toString().replace("-", "");
            String outName = fileId + ".jpg";
            File outFile = new File(UPLOAD_DIR, outName);

            // Read input stream and save
            try (InputStream in = ex.getRequestBody(); FileOutputStream out = new FileOutputStream(outFile)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) != -1) {
                    out.write(buf, 0, r);
                }
            }

            // Real image compression using Java ImageIO
            compressImage(outFile, outFile, 1280, 1280, 0.82f);

            String response = String.format("{\"url\":\"/uploads/%s\",\"media_type\":\"photo\"}", outName);
            sendJson(ex, 200, response);
        }
    }

    private static class MediaStreamHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            addCorsHeaders(ex);
            String path = ex.getRequestURI().getPath();
            String filename = path.replace("/uploads/", "");
            File file = new File(UPLOAD_DIR, filename);

            if (!file.exists()) {
                sendJson(ex, 404, "{\"error\":\"File Not Found\"}");
                return;
            }

            long length = file.length();
            String range = ex.getRequestHeaders().getFirst("Range");

            if (range != null && range.startsWith("bytes=")) {
                String[] parts = range.replace("bytes=", "").split("-");
                long start = Long.parseLong(parts[0]);
                long end = parts.length > 1 && !parts[1].isEmpty() ? Long.parseLong(parts[1]) : length - 1;
                end = Math.min(end, length - 1);
                long partLen = end - start + 1;

                Headers headers = ex.getResponseHeaders();
                headers.set("Content-Range", "bytes " + start + "-" + end + "/" + length);
                headers.set("Accept-Ranges", "bytes");
                headers.set("Content-Type", filename.endsWith(".mp4") ? "video/mp4" : "audio/mpeg");
                ex.sendResponseHeaders(206, partLen);

                try (RandomAccessFile raf = new RandomAccessFile(file, "r"); OutputStream os = ex.getResponseBody()) {
                    raf.seek(start);
                    byte[] buffer = new byte[8192];
                    long bytesRemaining = partLen;
                    while (bytesRemaining > 0) {
                        int read = raf.read(buffer, 0, (int) Math.min(buffer.length, bytesRemaining));
                        if (read == -1) break;
                        os.write(buffer, 0, read);
                        bytesRemaining -= read;
                    }
                }
            } else {
                Headers headers = ex.getResponseHeaders();
                headers.set("Content-Type", filename.endsWith(".mp4") ? "video/mp4" : "image/jpeg");
                ex.sendResponseHeaders(200, length);
                Files.copy(file.toPath(), ex.getResponseBody());
            }
        }
    }

    // --- Compression Algorithms in Java ---

    public static boolean compressImage(File inputFile, File outputFile, int maxWidth, int maxHeight, float quality) {
        try {
            BufferedImage orig = ImageIO.read(inputFile);
            if (orig == null) return false;

            int w = orig.getWidth();
            int h = orig.getHeight();

            if (w > maxWidth || h > maxHeight) {
                double scale = Math.min((double) maxWidth / w, (double) maxHeight / h);
                w = (int) (w * scale);
                h = (int) (h * scale);
            }

            BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = resized.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.drawImage(orig, 0, 0, w, h, null);
            g2d.dispose();

            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) return false;
            ImageWriter writer = writers.next();

            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);

            try (ImageOutputStream ios = ImageIO.createImageOutputStream(new FileOutputStream(outputFile))) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(resized, null, null), param);
            }
            writer.dispose();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean compressMediaWithFfmpeg(String inputPath, String outputPath, boolean isVideo) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(inputPath);
        if (isVideo) {
            cmd.addAll(Arrays.asList("-vcodec", "libx264", "-crf", "26", "-acodec", "aac", "-b:a", "128k", "-movflags", "+faststart"));
        } else {
            cmd.addAll(Arrays.asList("-vn", "-b:a", "128k", "-ar", "44100", "-ac", "2"));
        }
        cmd.add(outputPath);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process proc = pb.start();
            return proc.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // --- Helpers ---

    private static User authenticate(HttpExchange ex) {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) return null;
        String token = auth.replace("Bearer ", "").trim();
        return users.get(token);
    }

    private static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return password;
        }
    }

    private static void addCorsHeaders(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Authorization, Content-Type, Range");
    }

    private static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        addCorsHeaders(ex);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (Scanner s = new Scanner(ex.getRequestBody(), StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
            return s.hasNext() ? s.next() : "";
        }
    }

    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return "";
        int start = idx + pattern.length();
        int end = json.indexOf("\"", start);
        return end != -1 ? json.substring(start, end) : "";
    }

    private static String serializeUser(User u) {
        return String.format("{\"id\":\"%s\",\"username\":\"%s\",\"bio\":\"%s\",\"avatar_url\":\"%s\",\"role\":\"%s\",\"is_creator\":%d,\"custom_badge\":\"%s\",\"can_edit_calendar\":%d,\"can_manage_chat\":%d}",
                u.id, u.username, u.bio, u.avatarUrl, u.role, u.isCreator ? 1 : 0, u.customBadge, u.canEditCalendar ? 1 : 0, u.canManageChat ? 1 : 0);
    }

    private static String serializeChat(Chat c) {
        return String.format("{\"id\":\"%s\",\"type\":\"%s\",\"title\":\"%s\",\"created_by\":\"%s\",\"created_at\":%d}",
                c.id, c.type, c.title, c.createdBy, c.createdAt);
    }
}

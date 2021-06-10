package server;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private String currentPath = "C:\\";
    private String currentUserName;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        System.out.printf("Client connected ip: %S %n", socket.getLocalSocketAddress());
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())
        ) {
            while (true) {
                String command = in.readUTF();
                if ("COMMAND_EXIT".equals(command)) {
                    System.out.printf("Client disconnected ip: %S %n", socket.getLocalSocketAddress());
                    break;
                } else if (command.startsWith("COMMAND_UPLOAD ")) {
                    String[] pathCommand = command.split(" ", 2);
                    System.out.println("принимаю " + pathCommand[1]);
                    uploading(out, in, pathCommand[1]);
                } else if (command.startsWith("COMMAND_GETTREE_FROM_SERVER")) {
                    String[] pathCommand = command.split(" ", 2); //разбор пришедшей команды
                    String finalList = getDirTree(pathCommand[1]);
                    sendMessage(finalList, out, in);
                } else if (command.startsWith("COMMAND_DIR_OR_FILE")) { // проверка папка или файл
                    String[] pathCommand = command.split(" ", 2); //разбор пришедшей команды
                    File dirOrFile = new File(currentPath + pathCommand[1]);
                    if (dirOrFile.isDirectory()) {
                        currentPath = dirOrFile.toString();
                        if (!currentPath.endsWith("\\")) {
                            currentPath = currentPath + "\\";
                        }
                        out.writeUTF("COMMAND_DIR_OR_FILE dir " + currentPath);
                    } else out.writeUTF("COMMAND_DIR_OR_FILE file");
                } else if (command.startsWith("COMMAND_LEVEL_UP")) { // команда перехода вверх по иерархии папок
                    Path upperPath = Paths.get(currentPath).getParent();

                    if (upperPath != null && !currentPath.equals(String.format("C:\\" + currentUserName + "\\"))) { //доделать
                        currentPath = upperPath.toString();
                        if (!currentPath.endsWith("\\")) {
                            currentPath = currentPath + "\\";
                        }
                        sendMessage(getDirTree(currentPath), out, in);
                    }
                } else if (command.startsWith("COMMAND_DELETE ")) { //удаление файла
                    deleting(in, out, command);
                } else if (command.startsWith("COMMAND_NEW_FOLDER ")) {
                    String[] pathCommand = command.split(" ", 2);
                    try {
                        Files.createDirectory(Path.of(currentPath + pathCommand[1]));
                        System.out.println("создал папку " + pathCommand[1]);
                        out.writeUTF("COMMAND_NEW_FOLDER_STATUS " + pathCommand[1]);
                    } catch (IOException e) {
                        out.writeUTF("COMMAND_NEW_FOLDER_STATUS ERROR");
                    }
                } else if (command.startsWith("COMMAND_DOWNLOAD ")) {
                    String[] pathCommand = command.split(" ", 2);
                    try {
                        File file = new File(currentPath + pathCommand[1]);
                        if (!file.exists()) {
                            throw new FileNotFoundException();
                        }

                        long fileLength = file.length();
                        FileInputStream fis = new FileInputStream(file);

                        out.writeUTF("COMMAND_DOWNLOAD " + pathCommand[1]);
                        System.out.println("отдаю " + pathCommand[1]);
                        out.writeLong(fileLength);

                        int read = 0;
                        byte[] buffer = new byte[8 * 1024];
                        while ((read = fis.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                        fis.close();
                        out.flush();
                    } catch (FileNotFoundException e) {
                        System.err.println("File not found - " + pathCommand[1]);
                    }
                } else if (command.startsWith("COMMAND_AUTH ")) {
                    String[] pathCommand = command.split(" ", 3);
                    currentUserName = SQLHandler.getNicknameAndPassword(pathCommand[1], pathCommand[2]);
                    if (currentUserName == null) {
                        System.out.println("COMMAND_AUTH " + currentUserName);
                        sendMessage("COMMAND_AUTH_STATUS Логин или пароль не верный", out, in);
                    } else {
                        System.out.println("auth ok");
                        currentPath = currentPath + pathCommand[1];
                        if (!currentPath.endsWith("\\")) {
                            currentPath = currentPath + "\\";
                        }
                        sendMessage("COMMAND_AUTH_STATUS OK", out, in);
                    }
                } else if (command.startsWith("COMMAND_REG ")) {
                    String[] pathCommand = command.split(" ", 3);
                    if (pathCommand.length > 3) {
                        System.out.println("COMMAND_REG где то пробел");
                        continue; ///уведомить пользователя что логин с пробелом
                    }
                    boolean regSuccess = SQLHandler.registration(pathCommand[1], pathCommand[2]);
                    if (regSuccess) {
                        currentPath = currentPath + pathCommand[1];
                        if (!currentPath.endsWith("\\")) {
                            currentPath = currentPath + "\\";
                        }
                        Files.createDirectory(Path.of(currentPath));
                        currentUserName = pathCommand[1];
                        sendMessage("COMMAND_REG_STATUS OK", out, in);
                        System.out.println("зарегал");
                    } else sendMessage("COMMAND_REG_STATUS логин занят", out, in);
                } else if (command.startsWith("COMMAND_CHANGE_USER")){
                    Path path = Path.of(currentPath);
                    currentPath = path.getRoot().toString();
                }else if (command.startsWith("COMMAND_DELETE_AFTERMOVE")){
                    String[] pathCommand = command.split(" ", 2);
                    Files.delete(Path.of(currentPath + pathCommand[1]));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //получение списка фалов и папок по переданному пути в качетсве параметра

    private String getDirTree(String path) {
        //currentPath = path;
        String[] fileTree = new File(currentPath).list();
        StringBuilder sb = new StringBuilder("COMMAND_GETTREE_FROM_SERVER?" + currentPath);
        for (String s : fileTree) {
            sb.append("?").append(s);
        }
        return sb.toString(); // итоговая строка состоящая из команды "COMMAND_GETTREE_FROM_SERVER?папка?папка?файл"

    }

    //удаление на сервере
    private void deleting(DataInputStream in, DataOutputStream out, String s) {
        String[] pathCommand = s.split(" ", 2);
        System.out.println("удаляю " + pathCommand[1]);
        Path path = Path.of((currentPath + pathCommand[1]));
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) {
                        throw exc;
                    }
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            if (s.startsWith("COMMAND_DELETE ")) {
                sendMessage(String.format("COMMAND_DELETE_STATUS %s удален", pathCommand[1]), out, in);
            }
        } catch (IOException e) {
            sendMessage("COMMAND_DELETE_STATUS " + e.getMessage(), out, in);
        }
    }


    private void uploading(DataOutputStream out, DataInputStream in, String s) throws IOException {
        try {
            File file = new File(currentPath + s); // read file name
            if (!file.exists()) {
                file.createNewFile();
            } else {
                throw new IOException(s + " уже существует");
            }

            FileOutputStream fos = new FileOutputStream(file);
            long size = in.readLong();
            byte[] buffer = new byte[8 * 1024];
            for (int i = 0; i < (size + (8 * 1024 - 1)) / (8 * 1024); i++) {
                int read = in.read(buffer);
                fos.write(buffer, 0, read);
            }
            out.flush();
            fos.close();
            sendMessage(("COMMAND_UPLOAD_STATUS " + s), out, in);
            sendMessage(getDirTree(currentPath), out, in);
        } catch (IOException e) {
            sendMessage("COMMAND_UPLOAD_STATUS ERROR", out, in);
            sendMessage(e.getMessage(), out, in);
        }
    }


    public void sendMessage(String str, DataOutputStream out, DataInputStream in) {
        try {
            out.writeUTF(str);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}


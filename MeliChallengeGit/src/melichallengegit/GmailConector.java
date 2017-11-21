package melichallenge3;

import com.sun.mail.util.MailSSLSocketFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.search.FlagTerm;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;

public class GmailConector {

    static String mail_user;
    static String mail_pass;
    static String wordToSearch;
    static String mailFolder;
    static String dbName;
    static String TableName;
    static String MailProtocol;
    static String StoreName;
    static String MailDomain;
    static String Sqldriver;
    static String SqlUrl;
    static String SqlUser;
    static String SqlPass;

    public static void main(String args[]) {
        Properties prop = new Properties();
        InputStream input = null;
        try {
            input = new FileInputStream("config.properties");
            prop.load(input);

            wordToSearch = prop.getProperty("wordToSearch");
            mail_user = prop.getProperty("mail_user");
            mail_pass = prop.getProperty("mail_pass");
            mailFolder = prop.getProperty("mailFolder");
            dbName = prop.getProperty("dbName");
            TableName = prop.getProperty("TableName");
            MailProtocol = prop.getProperty("MailProtocol");
            StoreName = prop.getProperty("StoreName");
            MailDomain = prop.getProperty("MailDomain");
            Sqldriver = prop.getProperty("Sqldriver");
            SqlUrl = prop.getProperty("SqlUrl");
            SqlUser = prop.getProperty("SqlUser");
            SqlPass = prop.getProperty("SqlPass");

            searchWord(wordToSearch);
        } catch (IOException e) {
            System.out.println("Error al cargar el archivo de configuracion.");
            System.out.println(e.getMessage());
        }

    }

    public static void searchWord(String word) {
        Store store = setGmailConection();
        Connection conn = setMySqlConection();
        DbCreate(conn);

        try {
            Folder inbox = store.getFolder(mailFolder);
            inbox.open(Folder.READ_ONLY);
            FlagTerm ft = new FlagTerm(new Flags(), false);
            Message messages[] = inbox.search(ft);
            for (Message message : messages) {
                String subject = message.getSubject().toLowerCase();
                boolean enContent = false;
                enContent = searchInContent(message, word);

                if (subject.contains(word) || enContent) {
                    SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    String Fecha = sdfDate.format(message.getReceivedDate());
                    String From = message.getFrom()[0].toString();

                    System.out.println("Se encontró un mail que matcheo");
                    System.out.println("Fecha: " + Fecha);
                    System.out.println("From: " + From);
                    System.out.println("Subject: " + subject);
                    try {
                        PreparedStatement sql = conn.prepareStatement("USE " + dbName + ";");
                        sql.execute();
                        sql = conn.prepareStatement("INSERT INTO " + TableName + " (fecha, mail_from, mail_subject) VALUES ('" + Fecha + "','" + From + "','" + subject + "');");
                        sql.execute();
                        System.out.println("Registro ingresado con exito");
                    } catch (Exception e) {
                        System.out.println("Error al grabar el registro del mail: " + subject);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static Store setGmailConection() {
        Properties SysProps = System.getProperties();
        SysProps.setProperty("mail.store.protocol", MailProtocol);
        try {
            MailSSLSocketFactory socketFactory = new MailSSLSocketFactory();
            socketFactory.setTrustAllHosts(true);
            SysProps.put("mail.imaps.ssl.socketFactory", socketFactory);

            Session session = Session.getDefaultInstance(SysProps, null);
            //session.setDebug(true);
            Store store = session.getStore(MailProtocol);
            store.connect(MailDomain, mail_user, mail_pass);
            return store;
        } catch (Exception e) {
            System.out.println("Error en la conexion con Gmail");;
            return null;
        }
    }

    public static Connection setMySqlConection() {
        try {

            Class.forName(Sqldriver);
            Connection conn = DriverManager.getConnection(SqlUrl, SqlUser, SqlPass);

            System.out.println("Conectado a MySql");
            return conn;

        } catch (Exception ex) {
            System.out.println("Error conectando a MySql");
            return null;
        }
    }

    public static void DbCreate(Connection conn) {
        try {
            PreparedStatement sql = conn.prepareStatement("CREATE DATABASE IF NOT EXISTS " + dbName + ";");
            sql.execute();
            sql = conn.prepareStatement("USE " + dbName + ";");
            sql.execute();
            sql = conn.prepareStatement("CREATE TABLE IF NOT EXISTS " + TableName + "(id int not null auto_increment, fecha datetime, mail_from varchar(255), mail_subject varchar(255), PRIMARY KEY(id));");
            sql.execute();
            System.out.println("Base de datos creada");
        } catch (Exception ex) {
            System.out.println("Error al crear la base");
        }
    }

    public static boolean searchInContent(Part p, String word) {
        boolean retorno = false;
        try {
            Object o = p.getContent();
            if (o instanceof String) {
                String content = ((String) o).toLowerCase();
                if (content.contains(word)) {
                    retorno = true;
                }
            } else if (o instanceof Multipart) {
                Multipart mp = (Multipart) o;
                int count = mp.getCount();
                for (int i = 0; i < count; i++) {
                    retorno = searchInContent(mp.getBodyPart(i), word);
                    if (retorno) {
                        break;
                    }
                }
                return retorno;
            }
        } catch (Exception e) {
            System.out.println("Error al buscar en el content");
            return false;
        }
        return retorno;
    }

}

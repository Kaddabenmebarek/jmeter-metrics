package com.idorsia.research.jmetermetrics;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class Launcher {

	private static String jmeterBinPath;
	private static String testPlanPath;
	private static String csvResultFile;
	private static String htmlReportFolder;
	private static final String CSV_FILE = "-Results.csv";
	private static final String WINDOWS_ENV = "win";
	private static final String LINUX_ENV = "lin";

	public static void main(String[] args) throws IOException, AddressException, MessagingException {
		FileInputStream fi = new FileInputStream("/u00/idorsia/jmeter-results/jmeter.properties");
		ResourceBundle rb = new PropertyResourceBundle(fi);
		htmlReportFolder = rb.getString(LINUX_ENV + "HtmlReportFolder");
		System.out.println("Report Html Folder " + htmlReportFolder);
		System.out.println("Delete Report Html Folder");
		deleteReportFolder(new File(htmlReportFolder));
		System.out.println("Launch Jmeter process");
		launchJmeterProcess(rb, LINUX_ENV);
		launchEmailReport();
	}

	private static void deleteReportFolder(File file) {
		File[] contents = file.listFiles();
		if (contents != null) {
			for (File f : contents) {
				if (!Files.isSymbolicLink(f.toPath())) {
					deleteReportFolder(f);
				}
			}
		}
		file.delete();
	}

	private static void launchJmeterProcess(ResourceBundle rb, String prefixEnv) throws IOException {
		ProcessBuilder processBuilder = new ProcessBuilder();
		jmeterBinPath = rb.getString(prefixEnv + "JmeterBinPath");
		System.out.println("Jmeter bin path " + jmeterBinPath);
		testPlanPath = rb.getString(prefixEnv + "TestPlanPath");
		System.out.println("Test plan path " + testPlanPath);
		ZoneId zonedId = ZoneId.of("America/New_York");
		LocalDate today = LocalDate.now(zonedId);
		csvResultFile = rb.getString(prefixEnv + "CsvResultFile") + today + CSV_FILE;
		System.out.println("Input CSV File " + csvResultFile);
		if (prefixEnv.equals(WINDOWS_ENV)) { // WIN
			processBuilder.command("cmd.exe", "/c", jmeterBinPath + "jmeter.bat -n -t \"" + testPlanPath + "\" -l \""
					+ csvResultFile + "\" -e -o \"" + htmlReportFolder + "\"");
		} else { // LINUX
			processBuilder.command("bash", "-c", "sh " + jmeterBinPath + "jmeter -n -t \"" + testPlanPath + "\" -l \""
					+ csvResultFile + "\" -e -o \"" + htmlReportFolder + "\"");
		}
		try {
			System.out.println("ProcessBuilder Start");
			Process process = processBuilder.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			while ((line = reader.readLine()) != null) {
				System.out.println(line);
			}
			int exitCode = process.waitFor();
			if (exitCode == 0) {
				System.out.println("Process sucess!!");
			} else {
				System.out.println("Process failed!");
			}
			System.out.println("\nExited with code : " + exitCode);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private static void launchEmailReport() throws IOException, AddressException, MessagingException {
		FileInputStream fi = new FileInputStream("/u00/idorsia/jmeter-results/email.properties");
		ResourceBundle rb = new PropertyResourceBundle(fi);
		System.out.println("Parse Input CSV File");
		Map<String,String> res = parseCsvFile(csvResultFile);
		System.out.println("Buildin Message Body");
		String message = buildMessage(res);
		System.out.println("Send Email");
		sendJavaMail(message, rb);
	}

	private static String buildMessage(Map<String, String> res) {
		StringBuilder msg = new StringBuilder("This email is sent automatically.").append("<br />");
				msg.append("<b>JMeter</b> has proccessed api calls").append("<br />");
				msg.append("You will find attached the CSV file that contains Jmeter Results").append("<br /><br />");
				msg.append("Summary<br />");
				for(Entry<String,String> entry : res.entrySet()) {
					msg.append(" - ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" ms").append("<br/>");	
				}
				msg.append("<br />If you want to have more details and have a look on the generated dashboard, you can click on the link below").append("<br />");
				//msg.append(htmlReportFolder).append("index.html");
				msg.append("https://ares/jmeter-results/reports/");
				msg.append("</br></br><i>Please do not reply to this message.</i>");
		return msg.toString();
	}

	
	private static Map<String,String> parseCsvFile(String inputfile) throws FileNotFoundException, IOException {
		Map<String,String> res = new HashMap<String, String>();
		File sourceFile = new File(inputfile);
		FileReader fr = new FileReader(sourceFile);
		BufferedReader br = new BufferedReader(fr);
		String s = br.readLine();
		try {
			boolean firstLineRead = false;
			while (s != null) {
				if(!firstLineRead) {
					firstLineRead = true;
				}else {
					String[] values = s.split(",");
					res.put(values[2], values[1]);
				}
				s = br.readLine();
			}
		} finally {
			br.close();
		}
		return res;
	}
	
	public static void sendJavaMail(String message, ResourceBundle rb)
			throws AddressException, MessagingException, IOException {

		Properties props = new Properties();
		props.setProperty("mail.smtp.host", rb.getString("mail.host"));
		props.setProperty("mail.smtp.starttls.enable", rb.getString("mail.smtp.starttls.enable"));

		Session mailConnection = Session.getInstance(props, null);
		mailConnection.setDebug(false);
		Message msg = new MimeMessage(mailConnection);
		msg.setSentDate(new Date());

		Address recip = new InternetAddress(rb.getString("mail.to"));
		msg.setRecipient(Message.RecipientType.TO, recip);

		String[] ccs = rb.getString("mail.ccs").split(",");
		Address[] recipccs = new Address[ccs.length];
		for (int i =0; i<ccs.length;i++) {
			recipccs[i] = new InternetAddress(ccs[i]);
		}
		msg.setRecipients(Message.RecipientType.CC, recipccs);

		Address from = new InternetAddress(rb.getString("mail.from"));
		msg.setFrom(from);
		String subject = rb.getString("mail.subject");
		msg.setSubject(subject);

		MimeBodyPart messageBodyPart = new MimeBodyPart();
		messageBodyPart.setContent(message, "text/html");

		Multipart multipart = new MimeMultipart();
		multipart.addBodyPart(messageBodyPart);

		MimeBodyPart attachPart = new MimeBodyPart();
		attachPart.attachFile(csvResultFile);
		multipart.addBodyPart(attachPart);

		msg.setContent(multipart);
		Transport.send(msg);

	}

}

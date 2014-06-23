package es.radsys;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.log4j.Logger;

public class TareasExportacionSCAN implements Runnable {

	/**
	 * Código de error en el proceso de generación de ficheros PDW
	 */
	private static final String ERROR_STRING = "ERROR";
	/**
	 * Clave de la Propiedad en la que se almacena el último envío correcto.
	 */
	private static final String FECHA_ULTIMO_PROP = "fecha_ultimo_envio";
	/**
	 * Fichero de Propiedades en el que se almacena el último envío correcto.
	 */
	private static final String ULTIMO_PROPS_FILE = "./conf/ultimoEnvio.properties";
	/**
	 * Fichero de Propiedades de configuración del servicio.
	 */
	private static final String PROPS_FILE = "./conf/exportarSCAN.properties";
	private Logger log = Logger.getLogger(TareasExportacionSCAN.class);
	String username=null;
	String password=null;
//	SimpleDateFormat formateadorFechas = new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault());
	SimpleDateFormat formateadorDias = new SimpleDateFormat("yyyyMMdd",Locale.getDefault());

	String to = null;
	String smtpHost=null;
	String from=null;
	/**
	 * Asunto de los correos a enviar.
	 * IMPORTANTE debe coincidir con el nombre interno en la aplicación SCAN del gobierno de Navarra.
	 */
	String asunto=null;
	/**
	 * Directorio local de los ficheros PDW generados.
	 */
	String dirPDW=null;
	/**
	 * Directorio local de los ficheros generados por el SCADA.
	 */
	String dirSCADA=null;
	/**
	 * Nombre interno del SCADA. Propiedad "nombre_SCADA" del fichero "exportarSCAN.properties" 
	 * Nombre del emplazamiento, los que acompaña en el nombre del fichero xlsx a la fecha:
     * Por ejemplo en "20140325000018_Gobierno.xlsx" sería "Gobierno"
	 */
	String nombreSCADA=null;
	/**
	 * Periodicidad de los datos del SCADA.
	 */
	int intervaloDatos=15;
	private String[] cabecera={"'nombre","'HHMM"};
	String nombreSCADAfecha;
	/**
	 * Número de la Hoja de cálculo con los datos en el documento xlsx
	 */
	int numeroHoja=1;
	/**
	 * Formato de fecha de datos (24/03/2014 6:30:15), apto para constructor java.text.SimpleDateFormat:
   	 *  	new SimpleDateFormat("dd/MM/yyyy HH:mm:ss",Locale.getDefault());
	 */
	String formatoFechasDatos="dd/MM/yyyy HH:mm:ss";
	/**
	 * Numero de Parámetros a Enviar, deben venir en el fichero
	 */
	int numeroParametros=1;
	
	public void run() {
		log.info("TareasExportacionSCAN: "+System.currentTimeMillis());
		cargarPropiedades();
		for (String fecha: fechasPendientes()){
			log.info("Fecha a enviar: "+fecha);
			String adjunto=generarFichero(fecha);
			if(adjunto.compareTo(ERROR_STRING)!=0){
				if(enviarCorreo(adjunto)){
					guardarUltimoEnvio(fecha);
					log.info("Envio correcto: "+fecha);
				}
			}
		}
	}
	
	/**
	 * Alamcenar la fecha del último envío correcto en el fichero FECHA_ULTIMO_PROP
	 * @param fecha
	 */
	private void guardarUltimoEnvio(String fecha) {
		Properties propiedades = new Properties();
		propiedades.setProperty(FECHA_ULTIMO_PROP, fecha);
		FileOutputStream out;
		try {
			out = new FileOutputStream(ULTIMO_PROPS_FILE);
			propiedades.store(out, "fecha "+new Date());		
			out.close();
		} catch (FileNotFoundException e) {
			Logger.getLogger(getClass()).error(e);
			e.printStackTrace();
		} catch (IOException e) {
			Logger.getLogger(getClass()).error(e);
			e.printStackTrace();
		}

	}

	/**
	 * Método que devuelve un arraylist de fechas pendientes de envío.
	 * Desde la última fecha almacenada en el fichero conf/ultimoEnvio.properties
	 * hasta el día en curso.
	 * @return ArrayList<String> con los días pendientes como String en el formato "dd/MM/yyyy".
	 */
	private ArrayList<String> fechasPendientes() {
		ArrayList<String> fechasPendientes=new ArrayList<String>();
		GregorianCalendar fechaActual=new GregorianCalendar();
		GregorianCalendar fechaUltimoEnvio=dameFechaUltimoEnvio(fechaActual.getTime());
		// comenzamos al día siguiente del último envío
		fechaUltimoEnvio.add(Calendar.DATE, 1);
		while(fechaActual.after(fechaUltimoEnvio)){
			log.debug(formateadorDias.format(fechaUltimoEnvio.getTime()));
			fechasPendientes.add(formateadorDias.format(fechaUltimoEnvio.getTime()));
			fechaUltimoEnvio.add(Calendar.DATE, 1);
		}
		
		return fechasPendientes;
	}

	/**
	 * Método que lee la fecha almacenada en el fichero conf/ultimoEnvio.properties
	 * y la devuelve como  GregorianCalendar.
	 * @param date, la fecha inicializada a la fecha actual
	 * @return GregorianCalendar con la fecha del último día enviado.
	 */
	private GregorianCalendar dameFechaUltimoEnvio(Date date) {
		GregorianCalendar fechaUltimoEnvio = new GregorianCalendar();
		fechaUltimoEnvio.setTime(date);
		Properties propiedades = new Properties();
		try {
			propiedades.load(new FileInputStream(ULTIMO_PROPS_FILE));
			log.debug(propiedades);
			fechaUltimoEnvio.setTime(formateadorDias.parse(propiedades.getProperty(FECHA_ULTIMO_PROP)));
		} catch (FileNotFoundException e1) {
			Logger.getLogger(getClass()).error(e1);
			e1.printStackTrace();
		} catch (IOException e1) {
			Logger.getLogger(getClass()).error(e1);
			e1.printStackTrace();
		} catch (ParseException e) {
			Logger.getLogger(getClass()).error("Error parsear "+FECHA_ULTIMO_PROP+" en "+ULTIMO_PROPS_FILE+ " "+e);
			e.printStackTrace();
		}
		return fechaUltimoEnvio;
	}

	/**
	 * Método que recibe el día para el que hay que generar el fichero PDW.
	 * Lee la hoja de cálculo en el directorio configurado en exportarSCAN.properties->directorio_SCADA
	 * La hoja de cálculo tiene un nombre como AAAAMMDDHHMMSS_******.xlsx
	 * @param fecha del día de los datos a generar en el fichero
	 * @return String con el nombre del fichero generado en exportarSCAN.properties->directorio_PDW
	 */
	private String generarFichero(String fecha){
		nombreSCADAfecha=fecha;
		String adjunto=fecha+".dat";
		String filePDW = dirPDW+System.getProperty("file.separator")+adjunto;
		File buscarFicheroSCADA=new File(dirSCADA);
		File ficheroScada;
		// filtrar ficheros que comienzan con la fecha deseada y acaban con el nombre Scada + .xlsx
		FilenameFilter filter=new FilenameFilter() {
			public boolean accept(File dir, String name) {				
				if (name.startsWith(nombreSCADAfecha) && name.endsWith(nombreSCADA+".xlsx")) {
					return true;
				} else {
					return false;
				}
			}
		};
		File[] ficheros=buscarFicheroSCADA.listFiles(filter);
		switch (ficheros.length){
		case 0:
			adjunto=ERROR_STRING;
			log.error("no se encuentra fichero que comience con: "+nombreSCADAfecha+" en "+dirSCADA);
			return adjunto;
		case 1:
			ficheroScada=ficheros[0];
			break;
		default:
			log.error("Más de un fichero comienza con: "+nombreSCADAfecha+" en "+dirSCADA);
			ficheroScada=ficheros[0];
			break;			
		}

		if(ficheroScada.exists()){
			ParserFicherosSCADA parser = new ParserFicherosSCADA(ficheroScada,numeroHoja,formatoFechasDatos,numeroParametros);
			if(!parser.GenerarFicheroPDW(nombreSCADAfecha,filePDW,intervaloDatos,cabecera)){
				adjunto=ERROR_STRING;
				log.error("ERROR: al generar filePDW "+filePDW);
			}				
		}else{
			adjunto=ERROR_STRING;
			log.error("ERROR: ficheroScada "+nombreSCADAfecha +" no existe");
		}
		return adjunto;
	}

	private boolean enviarCorreo(String adjunto ){		
		
		Properties props = System.getProperties();
		// envio de mensaje por correo
		if(from!=null&&smtpHost!=null&&to!=null&&asunto!=null&&username!=null&&password!=null&&dirPDW!=null){
			props.put("mail.smtp.host",smtpHost);
			props.put("mail.smtp.auth", "true");
			props.put("mail.smtp.starttls.enable", "true");		// para gmail
			props.put("mail.smtp.port", "587");		// para gmail
//			props.put("mail.smtp.port", "111");		

			//			Session sesion = Session.getInstance(props);

			Session sesion = Session.getInstance(props,
					new javax.mail.Authenticator() {
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(username, password);
				}
			});
			try {
				Message mensaje = new MimeMessage(sesion);
				mensaje.setSubject(asunto);
				mensaje.setFrom(new InternetAddress(from));
				mensaje.addRecipient( Message.RecipientType.TO,
						new InternetAddress(to));// Receptor del mensaje
				mensaje.setText(adjunto);// Cuerpo del mensaje
				

		        MimeBodyPart messageBodyPart = new MimeBodyPart();

		        Multipart multipart = new MimeMultipart();

		        messageBodyPart = new MimeBodyPart();
		        String file = dirPDW+System.getProperty("file.separator")+adjunto;
		        DataSource source = new FileDataSource(file);
		        messageBodyPart.setDataHandler(new DataHandler(source));
		        messageBodyPart.setFileName(adjunto);
		        multipart.addBodyPart(messageBodyPart);

		        mensaje.setContent(multipart);
				
				
				Transport.send(mensaje);				// Se envía el mensaje
				log.info("Datos "+adjunto+ " enviado correctamente a: "+to);
			} catch (Exception e) {
				log.error("Error al enviar mensaje correo  "+adjunto+ " a "+ to);
				log.error(e);
				return false;
			}
		}
		else{
			log.info("valor null para envio de correo : from "+from+" smtpHost "+smtpHost+" to "+to+ " asunto "+asunto+" username "+username+ " password " +password+" dirPDW "+dirPDW);
			return false;
		}
		return true;

	}

	/**
	 * Método para leer y refrescar los parámetros de la aplicación en conf/exportarSCAN.properties
	 */
	private void cargarPropiedades() {
		Properties propiedades = new Properties();
		try {
			propiedades.load(new FileInputStream(PROPS_FILE));
		} catch (FileNotFoundException e1) {
			// TODO Auto-generado
			Logger.getLogger(getClass()).error(e1);
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generado
			Logger.getLogger(getClass()).error(e1);
			e1.printStackTrace();
		}

		log.debug(propiedades);
		
		if(propiedades!=null){
			try {
				from= propiedades.getProperty("mail_from");
				smtpHost=propiedades.getProperty("smtp_host");//smtp_host_alarmas
				to= propiedades.getProperty("mail_to");
				asunto= propiedades.getProperty("asunto");
				username= propiedades.getProperty("login");
				password= propiedades.getProperty("pass");
				dirPDW= propiedades.getProperty("directorio_PDW");
				dirSCADA= propiedades.getProperty("directorio_SCADA");
				nombreSCADA= propiedades.getProperty("nombre_SCADA");
				intervaloDatos= Integer.parseInt(propiedades.getProperty("periodo_datos_SCADA").trim());
				cabecera[0]=propiedades.getProperty("cabecera1");
				cabecera[1]=propiedades.getProperty("cabecera2");
				numeroHoja= Integer.parseInt(propiedades.getProperty("numero_hoja").trim());
				numeroParametros= Integer.parseInt(propiedades.getProperty("numero_parametros").trim());
			} catch (NumberFormatException e) {
				// TODO Auto-generado
				Logger.getLogger(getClass()).error("Error al parsear "+PROPS_FILE+" "+e);
				e.printStackTrace();
			}
		}
		
	}

}

package es.radsys;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;


public class ServicioExportacionSCAN {
	
	private static String version="0.0.1 (19/06/2014) ";
	private Logger log = Logger.getLogger(ServicioExportacionSCAN.class);
	private String log4JPropertyFile = "./conf/log4j.properties";

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		ServicioExportacionSCAN servicio = new ServicioExportacionSCAN();

	}

	public ServicioExportacionSCAN() {
		
		cargarLog4Properties();
		
		Properties propiedades = new Properties();
		try {
			propiedades.load(new FileInputStream("./conf/exportarSCAN.properties"));
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
		//	Iniciamos el sistema a la hora definida en las propiedades.
		GregorianCalendar fechaActual=new GregorianCalendar();
		long minutosHastaMedianoche=fechaActual.getMaximum(Calendar.HOUR_OF_DAY)-fechaActual.get(Calendar.HOUR_OF_DAY);
		minutosHastaMedianoche=60*minutosHastaMedianoche+fechaActual.getMaximum(Calendar.MINUTE)-fechaActual.get(Calendar.MINUTE);
		String minutoStr=propiedades.getProperty("Minuto");
		String horaStr=propiedades.getProperty("Hora");
		// por defecto se inicia a las 3:15 de la madrugada
		long delay=minutosHastaMedianoche+195;
		if(minutoStr!=null && horaStr!=null){
			try {
				delay=(minutosHastaMedianoche+Long.parseLong(minutoStr)+60*Long.parseLong(horaStr));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}else{
			log.error("Error en propiedades [minutoAlertas!=null && horaAlertas!=null]");
		}
		long frecuencia=60;
		String frecuenciaStr=propiedades.getProperty("Frecuencia");
		if(frecuenciaStr!=null){
			try {
				frecuencia=24*(Long.parseLong(frecuenciaStr));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}else{
			log.error("Error en propiedades [frecuenciaStr!=null]");
		}
		String message="Inicio en "+delay+ " minutos. Frecuencia "+frecuenciaStr+" ["+frecuencia+"] Hora inicio "+horaStr+" minuto en la hora "+minutoStr;
			
		log.info(message);
		ScheduledFuture<?> timeHandleAlarmas;
		final ScheduledExecutorService schedulerAlarmas = Executors.newSingleThreadScheduledExecutor();
//		timeHandleAlarmas=schedulerAlarmas.scheduleAtFixedRate(new es.radsys.TareasExportacionSCAN(), delay, frecuencia, TimeUnit.MINUTES);
		// en desarrollo se inicia al momento
		timeHandleAlarmas=schedulerAlarmas.scheduleAtFixedRate(new es.radsys.TareasExportacionSCAN(), 0, 1, TimeUnit.MINUTES);

	}
	
	public void cargarLog4Properties(){
		Properties p = new Properties();
		try {
			p.load(new FileInputStream(log4JPropertyFile));
			PropertyConfigurator.configure(p);

		} catch (IOException e) {
			System.out.println("Error al cargar fichero de configuracion log4"+e);
			String log4JPropertyFileLocal = "src/log4j.properties";
			try{
				p.load(new FileInputStream(log4JPropertyFileLocal));
				PropertyConfigurator.configure(p);
			}catch(IOException e1){
				System.out.println("Error al cargar fichero de configuracion interno log4"+e1);
			}
		}

	}

}

package es.radsys;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.apache.log4j.Logger;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class ParserFicherosSCADA {	

	List cellDataList;
	/**
	 * Formato de fecha de datos (24/03/2014 6:30:15), apto para constructor java.text.SimpleDateFormat:
   	 *  	new SimpleDateFormat("dd/MM/yyyy HH:mm:ss",Locale.getDefault());
	 */
	String formatoFechasDatos="dd/MM/yyyy HH:mm:ss";
	SimpleDateFormat formateadorFechas = new SimpleDateFormat(formatoFechasDatos,Locale.getDefault());
	/**
	 * Numero de Parámetros a Enviar, deben venir en el fichero
	 */
	int numeroParametros=1;
	SimpleDateFormat formateadorDias = new SimpleDateFormat("yyyyMMdd",Locale.getDefault());
	SimpleDateFormat formateadorHHmm = new SimpleDateFormat("HHmm",Locale.getDefault());
	
	public ParserFicherosSCADA(File fileName, int numeroHoja, String formatoFechasDatos, int numeroParametros) {
		this.formatoFechasDatos=formatoFechasDatos;
		this.numeroParametros=numeroParametros;
		formateadorFechas = new SimpleDateFormat(formatoFechasDatos,Locale.getDefault());
		
		cellDataList = new ArrayList();
		try{
			FileInputStream fileInputStream = new FileInputStream( fileName);
			XSSFWorkbook workBook = new XSSFWorkbook(fileInputStream);
			XSSFSheet hssfSheet = workBook.getSheetAt(numeroHoja);
			Iterator rowIterator = hssfSheet.rowIterator();
			while (rowIterator.hasNext()){
				XSSFRow hssfRow = (XSSFRow) rowIterator.next();
				Iterator iterator = hssfRow.cellIterator();
				List cellTempList = new ArrayList();
				while (iterator.hasNext()){
					XSSFCell hssfCell = (XSSFCell) iterator.next();
					cellTempList.add(hssfCell);
				}
				cellDataList.add(cellTempList);
			}
		}catch (Exception e)
		{e.printStackTrace();}
//		Leer(cellDataList);
	}

	void Leer(List cellDataList){
		for (int i = 0; i < cellDataList.size(); i++){
			List cellTempList = (List) cellDataList.get(i);
			for (int j = 0; j < cellTempList.size(); j++){
				XSSFCell hssfCell = (XSSFCell) cellTempList.get(j);
				String stringCellValue = hssfCell.toString();
				System.out.print(stringCellValue+">>>");
			}
			System.out.println();
		}
	}
	
	boolean GenerarFicheroPDW(String nombreSCADAfecha, String filePDW, int frecuencia, String[] cabecera){
		try {
			BufferedWriter out=new BufferedWriter(new FileWriter(filePDW));
			// escribir cabeceras
			out.write(cabecera[0]);
			out.newLine();
			out.write(cabecera[1]);
			out.newLine();

			GregorianCalendar fechaEsperada=new GregorianCalendar();
			GregorianCalendar fechaDato;
			try {
				fechaEsperada.setTimeInMillis(formateadorDias.parse(nombreSCADAfecha).getTime());
				// los ficheros tienen datos del día anterior a la fecha del nombre
				fechaEsperada.add(Calendar.DATE, -1);
				// además el primer dato en verdad corresponde al último del día anterior
				fechaEsperada.add(Calendar.MINUTE, 1);
			} catch (ParseException e) {
				Logger.getLogger(getClass()).error("Error al parsear la fechaEsperada: "+nombreSCADAfecha+" "+e);
				e.printStackTrace();
			}
			
			// leer celdas de excel desde la segunda fila y generar filas en fichero PDW
			for (int i = 1; i < cellDataList.size(); i++){		
				
				String linea="";
				int indice=1;
				List cellTempList = (List) cellDataList.get(i);
				// leer la fecha y parsearla y comparar con la fecha esperada de inicio
				XSSFCell hssfCell = (XSSFCell) cellTempList.get(0);
				fechaDato=new GregorianCalendar();
				try {
					fechaDato.setTimeInMillis(formateadorFechas.parse(hssfCell.toString()).getTime());
				} catch (ParseException e) {
					Logger.getLogger(getClass()).error("Error al parsear la fechaEsperada: "+nombreSCADAfecha+" "+e);
					e.printStackTrace();
					continue;
				}
				if(fechaDato!=null)
					fechaDato.set(Calendar.SECOND, 0);
				if(fechaDato.before(fechaEsperada)){
					Logger.getLogger(getClass()).info("fechaDato.before(fechaEsperada) en fileName "+nombreSCADAfecha+" fechaDato "+formateadorFechas.format(fechaDato.getTime()) );
					continue;
				}else
					linea=formateadorHHmm.format(fechaDato.getTime())+" ";
					
				// si la fecha es 0000 del día siguiente ponemos 2400 que es lo que dice el formato PDW 
				if(linea.compareTo("0000 ")==0)
					linea="2400 ";
				
				for (int j = 0; j < numeroParametros; j++){
					hssfCell = (XSSFCell) cellTempList.get(indice);
					String stringCellValue = hssfCell.toString();	
					linea=linea+stringCellValue+"V ";
					indice=indice+2;
				}
				out.write(linea);
				out.newLine();
				Logger.getLogger(getClass()).debug(linea);
			}
			
			out.flush();
			out.close();
			
		} catch (IOException e) {
			Logger.getLogger(getClass()).error(e);
			e.printStackTrace();
		}
		return true;
	}
}

/**
 *  SQLConnection
 *  Author: thaitruongminh
 *  Description: 
 *   00: Test DBMS Connection
 *   01: Test executeUpdate with CREATE DATABASE statement
 *   02: Test executeUpdate with CREATE TABLE statement
 *   03: Test executeUpdate with INSERT statement
 *   04: Test executeUpdate with UPDATE statement
 *   05: Test executeUpdate with DELETE statement
 *   06: Test executeQuery with SELECT statement
 *   07: Test executeUpdate with DROP TABLE statement
 */

model MSSQL_07

/* Insert your model definition here */

  
global {

	init {
		create species: toto number: 1 ;
	}
}  
entities {  
	species toto skills: [MAELIADBMS] {  
		var listRes type: list init:[];
		//var obj type: obj;
		reflex {
			do action: helloWorld;			 
			// 
			do action: executeUpdateDB{ 
 				arg vendorName value: "MSSQL";
				arg url value: "193.49.54.112";
				arg port value: "1433";
				arg dbName value: "STUDENTS";
				arg usrName value: "sa";
				arg password value: "tmt";
 				arg updateComm value: "DROP TABLE REGISTRATION ";
 			}
		}
	} 
}      
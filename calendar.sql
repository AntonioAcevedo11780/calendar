CREATE DATABASE  IF NOT EXISTS `calendar` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;
USE `calendar`;
-- MySQL dump 10.13  Distrib 8.0.34, for Win64 (x86_64)
--
-- Host: localhost    Database: calendar
-- ------------------------------------------------------
-- Server version	8.0.30

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `calendar_permissions`
--

DROP TABLE IF EXISTS `calendar_permissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `calendar_permissions` (
  `PERMISSION_ID` varchar(10) NOT NULL,
  `CALENDAR_ID` varchar(10) NOT NULL,
  `USER_ID` varchar(10) NOT NULL,
  `PERMISSION_TYPE` varchar(20) DEFAULT NULL,
  `SHARED_DATE` datetime DEFAULT CURRENT_TIMESTAMP,
  `ACTIVE` char(1) DEFAULT 'Y',
  PRIMARY KEY (`PERMISSION_ID`),
  KEY `CALENDAR_ID` (`CALENDAR_ID`),
  KEY `USER_ID` (`USER_ID`),
  CONSTRAINT `calendar_permissions_ibfk_1` FOREIGN KEY (`CALENDAR_ID`) REFERENCES `calendars` (`CALENDAR_ID`),
  CONSTRAINT `calendar_permissions_ibfk_2` FOREIGN KEY (`USER_ID`) REFERENCES `users` (`USER_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `calendar_permissions`
--

LOCK TABLES `calendar_permissions` WRITE;
/*!40000 ALTER TABLE `calendar_permissions` DISABLE KEYS */;
/*!40000 ALTER TABLE `calendar_permissions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `calendars`
--

DROP TABLE IF EXISTS `calendars`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `calendars` (
  `CALENDAR_ID` varchar(10) NOT NULL,
  `OWNER_ID` varchar(10) NOT NULL,
  `NAME` varchar(100) NOT NULL,
  `DESCRIPTION` varchar(500) DEFAULT NULL,
  `COLOR` varchar(7) DEFAULT NULL,
  `ACTIVE` char(1) DEFAULT 'Y',
  `CREATED_DATE` datetime DEFAULT CURRENT_TIMESTAMP,
  `MODIFIED_DATE` datetime DEFAULT NULL,
  PRIMARY KEY (`CALENDAR_ID`),
  KEY `OWNER_ID` (`OWNER_ID`),
  CONSTRAINT `calendars_ibfk_1` FOREIGN KEY (`OWNER_ID`) REFERENCES `users` (`USER_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `calendars`
--

LOCK TABLES `calendars` WRITE;
/*!40000 ALTER TABLE `calendars` DISABLE KEYS */;
INSERT INTO `calendars` VALUES ('CAL0000001','USR0000001','Mis Clases','Horario de clases UTEZ','#1976D2','Y','2025-06-14 04:41:34',NULL),('CAL0000002','USR0000001','Tareas y Proyectos','Entregas importantes','#E53935','Y','2025-06-14 04:41:34',NULL),('CAL0000003','USR0000001','Personal','Eventos personales','#388E3C','Y','2025-06-14 04:41:34',NULL),('CAL0000004','USR0000001','Exámenes','Fechas de exámenes','#F57C00','Y','2025-06-14 04:41:34',NULL);
/*!40000 ALTER TABLE `calendars` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `class_templates`
--

DROP TABLE IF EXISTS `class_templates`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `class_templates` (
  `TEMPLATE_ID` varchar(10) NOT NULL,
  `TEACHER_ID` varchar(10) NOT NULL,
  `TEMPLATE_NAME` varchar(100) NOT NULL,
  `SUBJECT` varchar(100) DEFAULT NULL,
  `GROUP_NAME` varchar(50) DEFAULT NULL,
  `DURATION_MINUTES` int DEFAULT NULL,
  `WEEK_DAYS` varchar(20) DEFAULT NULL,
  `START_TIME` time DEFAULT NULL,
  `CREATED_DATE` datetime DEFAULT CURRENT_TIMESTAMP,
  `ACTIVE` char(1) DEFAULT 'Y',
  PRIMARY KEY (`TEMPLATE_ID`),
  KEY `TEACHER_ID` (`TEACHER_ID`),
  CONSTRAINT `class_templates_ibfk_1` FOREIGN KEY (`TEACHER_ID`) REFERENCES `users` (`USER_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `class_templates`
--

LOCK TABLES `class_templates` WRITE;
/*!40000 ALTER TABLE `class_templates` DISABLE KEYS */;
/*!40000 ALTER TABLE `class_templates` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `events`
--

DROP TABLE IF EXISTS `events`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `events` (
  `EVENT_ID` varchar(10) NOT NULL,
  `CALENDAR_ID` varchar(10) NOT NULL,
  `CREATOR_ID` varchar(10) NOT NULL,
  `TITLE` varchar(100) NOT NULL,
  `DESCRIPTION` text,
  `START_DATE` datetime NOT NULL,
  `END_DATE` datetime DEFAULT NULL,
  `ALL_DAY` char(1) DEFAULT 'N',
  `LOCATION` varchar(200) DEFAULT NULL,
  `RECURRENCE` varchar(20) DEFAULT NULL,
  `RECURRENCE_END_DATE` datetime DEFAULT NULL,
  `ACTIVE` char(1) DEFAULT 'Y',
  `CREATED_DATE` datetime DEFAULT CURRENT_TIMESTAMP,
  `MODIFIED_DATE` datetime DEFAULT NULL,
  PRIMARY KEY (`EVENT_ID`),
  KEY `CALENDAR_ID` (`CALENDAR_ID`),
  KEY `CREATOR_ID` (`CREATOR_ID`),
  CONSTRAINT `events_ibfk_1` FOREIGN KEY (`CALENDAR_ID`) REFERENCES `calendars` (`CALENDAR_ID`),
  CONSTRAINT `events_ibfk_2` FOREIGN KEY (`CREATOR_ID`) REFERENCES `users` (`USER_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `events`
--

LOCK TABLES `events` WRITE;
/*!40000 ALTER TABLE `events` DISABLE KEYS */;
INSERT INTO `events` VALUES ('EVT0000001','CAL0000001','USR0000001','Clase Virtual','Metodologías de desarrollo','2025-06-14 09:00:00','2025-06-14 11:00:00','N',NULL,NULL,NULL,'Y','2025-06-14 04:41:34',NULL),('EVT0000002','CAL0000002','USR0000001','Revisar Proyecto','Avances del proyecto web','2025-06-14 15:00:00','2025-06-14 17:00:00','N',NULL,NULL,NULL,'Y','2025-06-14 04:41:34',NULL),('EVT0000003','CAL0000001','USR0000001','Programación Web','Java con Spring Boot','2025-06-16 08:00:00','2025-06-16 10:00:00','N',NULL,NULL,NULL,'Y','2025-06-14 04:41:34',NULL),('EVT0000004','CAL0000001','USR0000001','Base de Datos','MySQL y diseño','2025-06-17 10:30:00','2025-06-17 12:30:00','N',NULL,NULL,NULL,'Y','2025-06-14 04:41:34',NULL),('EVT0000005','CAL0000004','USR0000001','Examen Parcial BD','Segundo parcial','2025-06-19 10:00:00','2025-06-19 12:00:00','N',NULL,NULL,NULL,'Y','2025-06-14 04:41:34',NULL),('EVT0000006','CAL0000002','USR0000001','Entrega Proyecto Web','Proyecto final','2025-06-20 23:59:00','2025-06-20 23:59:00','N',NULL,NULL,NULL,'Y','2025-06-14 04:41:34',NULL);
/*!40000 ALTER TABLE `events` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `invitations`
--

DROP TABLE IF EXISTS `invitations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `invitations` (
  `INVITATION_ID` varchar(10) NOT NULL,
  `CALENDAR_ID` varchar(10) NOT NULL,
  `INVITED_USER_ID` varchar(10) NOT NULL,
  `INVITER_USER_ID` varchar(10) NOT NULL,
  `PERMISSION_TYPE` varchar(20) DEFAULT NULL,
  `STATUS` varchar(20) DEFAULT 'PENDING',
  `INVITATION_DATE` datetime DEFAULT CURRENT_TIMESTAMP,
  `RESPONSE_DATE` datetime DEFAULT NULL,
  PRIMARY KEY (`INVITATION_ID`),
  KEY `CALENDAR_ID` (`CALENDAR_ID`),
  KEY `INVITED_USER_ID` (`INVITED_USER_ID`),
  KEY `INVITER_USER_ID` (`INVITER_USER_ID`),
  CONSTRAINT `invitations_ibfk_1` FOREIGN KEY (`CALENDAR_ID`) REFERENCES `calendars` (`CALENDAR_ID`),
  CONSTRAINT `invitations_ibfk_2` FOREIGN KEY (`INVITED_USER_ID`) REFERENCES `users` (`USER_ID`),
  CONSTRAINT `invitations_ibfk_3` FOREIGN KEY (`INVITER_USER_ID`) REFERENCES `users` (`USER_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `invitations`
--

LOCK TABLES `invitations` WRITE;
/*!40000 ALTER TABLE `invitations` DISABLE KEYS */;
/*!40000 ALTER TABLE `invitations` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `USER_ID` varchar(10) NOT NULL,
  `MATRICULA` varchar(20) DEFAULT NULL,
  `EMAIL` varchar(50) NOT NULL,
  `FIRST_NAME` varchar(40) NOT NULL,
  `LAST_NAME` varchar(40) NOT NULL,
  `PASSWORD` varchar(255) NOT NULL,
  `ROLE` enum('alumno','docente','admin') DEFAULT NULL,
  `ACTIVE` char(1) DEFAULT 'Y',
  `CREATED_DATE` datetime DEFAULT CURRENT_TIMESTAMP,
  `LAST_LOGIN` datetime DEFAULT NULL,
  PRIMARY KEY (`USER_ID`),
  UNIQUE KEY `EMAIL` (`EMAIL`),
  UNIQUE KEY `MATRICULA` (`MATRICULA`),
  KEY `idx_users_matricula` (`MATRICULA`),
  KEY `idx_users_email_domain` (`EMAIL`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES ('USR0000001','20243ds076','20243ds076@utez.edu.mx','Antonio','Acevedo','123456','alumno','Y','2025-06-14 04:41:34','2025-06-18 01:13:47'),('USR0000002','20243ds075','20243ds075@utez.edu.mx','Daniel','Arroyo','123456','alumno','Y','2025-06-14 04:41:34',NULL),('USR0000003','20243ds085','20243ds085@utez.edu.mx','Carlos','Gonz','123456','alumno','Y','2025-06-14 04:41:34',NULL),('USR0000004','aldoromero','aldoromero@utez.edu.mx','Aldo','Romero','123456','docente','Y','2025-06-14 04:41:34',NULL),('USR0000005','mariaperez','mariaperez@utez.edu.mx','María','Pérez','123456','docente','Y','2025-06-14 04:41:34',NULL),('USR0000006','admin','admin@utez.edu.mx','Administrador','Sistema','123456','admin','Y','2025-06-14 04:41:34',NULL);
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-06-17 19:27:02

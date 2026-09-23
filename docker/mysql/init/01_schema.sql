-- MySQL dump 10.13  Distrib 8.0.40, for Win64 (x86_64)
--
-- Host: localhost    Database: internship_db
-- ------------------------------------------------------
-- Server version	8.0.40

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `apply_audit_history`
--

DROP TABLE IF EXISTS `apply_audit_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `apply_audit_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `apply_id` bigint NOT NULL COMMENT '????ID',
  `node_name` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '???? (TEACHER_AUDIT:????, DEPT_AUDIT:????)',
  `auditor_id` bigint NOT NULL COMMENT '?????ID',
  `auditor_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '?????',
  `auditor_role` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '????? (TEACHER, DEPT_ADMIN)',
  `audit_action` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '???? (APPROVED:??, REJECTED:??)',
  `audit_opinion` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '???? (?????trim????5??)',
  `snapshot_data` longtext COLLATE utf8mb4_unicode_ci COMMENT '?????????? (JSON??)',
  `audit_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '???? (??????????)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`),
  KEY `idx_audit_apply_id` (`apply_id`,`audit_time`)
) ENGINE=InnoDB AUTO_INCREMENT=157 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='?????????????';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `base_class`
--

DROP TABLE IF EXISTS `base_class`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `base_class` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `dept_id` bigint NOT NULL COMMENT '鎵?睘闄㈢郴ID',
  `major_id` bigint NOT NULL COMMENT '鎵?睘涓撲笟ID',
  `class_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鐝?骇浠ｇ爜',
  `class_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鐝?骇鍚嶇О',
  `grade` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '骞寸骇/鍏ュ?骞翠唤',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '鐘舵? (1:鍚?敤, 0:鍋滅敤)',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎 (0:姝ｅ父, 1:鍒犻櫎)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_code` (`class_code`),
  KEY `idx_class_dept_major` (`dept_id`,`major_id`),
  KEY `idx_class_status` (`status`,`is_deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='琛屾斂鐝?骇琛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `base_department`
--

DROP TABLE IF EXISTS `base_department`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `base_department` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `dept_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '闄㈢郴缂栫爜',
  `dept_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '闄㈢郴鍚嶇О',
  `leader_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '璐熻矗浜哄?鍚',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鑱旂郴鐢佃瘽',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '鐘舵? (1:鍚?敤, 0:鍋滅敤)',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎 (0:姝ｅ父, 1:鍒犻櫎)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dept_code` (`dept_code`),
  KEY `idx_dept_status` (`status`,`is_deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浜岀骇闄㈢郴琛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `base_major`
--

DROP TABLE IF EXISTS `base_major`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `base_major` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `dept_id` bigint NOT NULL COMMENT '鎵?睘闄㈢郴ID',
  `major_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '涓撲笟缂栫爜',
  `major_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '涓撲笟鍚嶇О',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '鐘舵? (1:鍚?敤, 0:鍋滅敤)',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎 (0:姝ｅ父, 1:鍒犻櫎)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_major_code` (`major_code`),
  KEY `idx_major_dept_id` (`dept_id`),
  KEY `idx_major_status` (`status`,`is_deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='涓撲笟淇℃伅琛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `internship_apply`
--

DROP TABLE IF EXISTS `internship_apply`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `internship_apply` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `task_id` bigint NOT NULL COMMENT '??????ID',
  `student_id` bigint NOT NULL COMMENT '??????ID',
  `student_number` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '??',
  `student_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '????',
  `dept_id` bigint NOT NULL COMMENT '??????ID',
  `major_id` bigint DEFAULT NULL COMMENT '????ID',
  `class_id` bigint DEFAULT NULL COMMENT '????ID',
  `company_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '??????',
  `job_position` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '??????',
  `job_address` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '??????',
  `company_contact_person` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '??????/?????',
  `company_contact_phone` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '?????',
  `company_contact_email` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '?????',
  `start_date` date NOT NULL COMMENT '??????',
  `end_date` date NOT NULL COMMENT '??????',
  `internship_mode` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DISTRIBUTED' COMMENT '???? (CENTRALIZED:??, DISTRIBUTED:??)',
  `job_duties` text COLLATE utf8mb4_unicode_ci COMMENT '?????????',
  `agreement_file_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '??????/???????',
  `apply_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '???? (DRAFT:??, SUBMITTED:???, TEACHER_APPROVED:???????, TEACHER_REJECTED:????, APPROVED:??????, DEPT_REJECTED:????)',
  `is_locked` tinyint NOT NULL DEFAULT '0' COMMENT '?????? (0:???, 1:???????? APPLY-009)',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`),
  KEY `idx_apply_student_task` (`student_id`,`task_id`),
  KEY `idx_apply_dept_status` (`dept_id`,`apply_status`,`is_deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=638 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='????????';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `internship_archive`
--

DROP TABLE IF EXISTS `internship_archive`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `internship_archive` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '鍗峰畻涓婚敭ID',
  `archive_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鐢靛瓙鍗峰畻鍞?竴褰掓。缂栧彿 (ARC+灞婂埆+瀛﹀彿)',
  `task_id` bigint unsigned NOT NULL COMMENT '鎵?睘瀹炰範浠诲姟ID',
  `student_id` bigint unsigned NOT NULL COMMENT '瀛︾敓ID',
  `dept_id` bigint unsigned NOT NULL COMMENT '浜岀骇闄㈢郴ID',
  `academic_year` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '褰掓。瀛﹀勾瀛︽湡',
  `check_matrix_json` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '褰掓。9椤硅瘖鏂?牳楠屽揩鐓?SON',
  `archive_bundle_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鐢靛瓙妗ｆ?琚嬪畬鏁碯IP鎵撳寘鏂囦欢鐩稿?瀛樺偍璺?緞',
  `archive_pdf_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鏍囧噯鍖栧疄涔犵櫥璁拌〃PDF鐩稿?瀛樺偍璺?緞',
  `archived_user_id` bigint unsigned NOT NULL COMMENT '鎵ц?褰掓。閿佸畾鎿嶄綔浜篒D',
  `archived_time` datetime NOT NULL COMMENT '褰掓。鍐荤粨鏃堕棿',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ARCHIVED' COMMENT '鐘舵?: ARCHIVED(宸插綊妗ｉ攣瀹?, SPECIAL_UNLOCKED(鐗规壒瑙ｉ攣璋冩敼涓?',
  `special_unlock_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鐗规壒瑙ｉ攣浜嬬敱',
  `special_doc_no` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '绾夸笅鐗规壒绾㈠ご鎵规枃鍙',
  `unlocked_by` bigint unsigned DEFAULT NULL COMMENT '瑙ｉ攣瓒呯?ID',
  `unlocked_time` datetime DEFAULT NULL COMMENT '鐗规壒瑙ｉ攣璧峰?鏃堕棿',
  `unlock_expire_time` datetime DEFAULT NULL COMMENT '鐗规壒瑙ｉ攣鍒版湡鎴??鏃堕棿',
  `version` int unsigned NOT NULL DEFAULT '1' COMMENT '褰掓。鐗堟湰鍙?(鍐嶆?褰掓。閫掑?)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎: 0-姝ｅ父, 1-鍒犻櫎',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_archive_no` (`archive_no`),
  UNIQUE KEY `uk_archive_task_student` (`task_id`,`student_id`),
  KEY `idx_archive_dept_year` (`dept_id`,`academic_year`),
  KEY `idx_archive_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=96 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='瀹炰範鐢靛瓙鍗峰畻褰掓。涓昏〃';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `internship_guidance_record`
--

DROP TABLE IF EXISTS `internship_guidance_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `internship_guidance_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` bigint NOT NULL COMMENT '实习任务ID',
  `teacher_id` bigint NOT NULL COMMENT '指导教师用户ID',
  `teacher_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '指导教师姓名',
  `student_id` bigint NOT NULL COMMENT '被指导学生用户ID',
  `student_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '被指导学生姓名',
  `dept_id` bigint NOT NULL COMMENT '二级院系ID',
  `guidance_date` datetime NOT NULL COMMENT '指导开展时间 (GUIDANCE-003)',
  `guidance_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '指导方式 (PHONE:电话, ONLINE:网络, ONSITE:实地走访, EMAIL_OTHER:其他)',
  `content_summary` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '交流内容要点 (GUIDANCE-003)',
  `student_feedback` text COLLATE utf8mb4_unicode_ci COMMENT '学生在岗确认反馈 (API-067)',
  `feedback_time` datetime DEFAULT NULL COMMENT '首次确认反馈时间，确认后不可修改',
  `feedback_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UNCONFIRMED' COMMENT '反馈确认状态 (UNCONFIRMED:未确认, CONFIRMED:已确认锁定)',
  `followup_actions` text COLLATE utf8mb4_unicode_ci COMMENT '后续关注与改进跟进举措',
  `location` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '走访地点 (实地走访时必填)',
  `attachment_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '走访凭证/谈话记录佐证材料URL (引用路径)',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_guidance_teacher` (`teacher_id`,`task_id`),
  KEY `idx_guidance_student_date` (`student_id`,`guidance_date`),
  KEY `idx_guidance_dept` (`dept_id`,`guidance_date`)
) ENGINE=InnoDB AUTO_INCREMENT=110344 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='指导教师过程指导与走访台账表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `internship_task`
--

DROP TABLE IF EXISTS `internship_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `internship_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `task_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '????',
  `task_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '????',
  `dept_id` bigint NOT NULL COMMENT '??????ID',
  `academic_year` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '???? (? 2025-2026)',
  `semester` tinyint NOT NULL COMMENT '???? (1:????, 2:????)',
  `internship_mode` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DISTRIBUTED' COMMENT '???? (CENTRALIZED:??, DISTRIBUTED:??, HYBRID:??)',
  `start_date` date NOT NULL COMMENT '??????',
  `end_date` date NOT NULL COMMENT '??????',
  `weight_enterprise` decimal(5,2) DEFAULT NULL COMMENT '企业评价成绩权重(%)',
  `weight_teacher_process` decimal(5,2) DEFAULT NULL COMMENT '指导教师过程评价权重(%)',
  `weight_weekly_report` decimal(5,2) DEFAULT NULL COMMENT '周报综合成绩权重(%)',
  `weight_stage_material` decimal(5,2) DEFAULT NULL COMMENT '阶段材料成绩权重(%)',
  `weight_summary` decimal(5,2) DEFAULT NULL COMMENT '实习总结成绩权重(%)',
  `material_checklist` text COLLATE utf8mb4_unicode_ci COMMENT '???????? (JSON??)',
  `grade_rules_json` text COLLATE utf8mb4_unicode_ci COMMENT '浠诲姟绾т簲绾у埗绛夌?鍒掑垎瑙勫垯閰嶇疆JSON',
  `weekly_frequency` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '周报提交频次 (WEEKLY, BIWEEKLY)',
  `weekly_deadline_day` tinyint DEFAULT NULL COMMENT '每周周报截止日 (1:周一 ~ 7:周日)',
  `safety_passing_score` int DEFAULT NULL COMMENT '安全考试及格分',
  `safety_max_attempts` int DEFAULT NULL COMMENT '安全考试最大允许重测次数',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '???? (DRAFT:??, PUBLISHED:???, IN_PROGRESS:???, ENDED:???, ARCHIVED:???)',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '???? (0:??, 1:??)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_code` (`task_code`),
  KEY `idx_task_dept` (`dept_id`),
  KEY `idx_task_status` (`status`,`is_deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=2035 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='????????';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `internship_task_class`
--

DROP TABLE IF EXISTS `internship_task_class`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `internship_task_class` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `task_id` bigint NOT NULL COMMENT '????ID',
  `class_id` bigint NOT NULL COMMENT '??ID',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_class` (`task_id`,`class_id`),
  KEY `idx_class_task` (`class_id`,`task_id`)
) ENGINE=InnoDB AUTO_INCREMENT=56 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='???????';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `internship_task_major`
--

DROP TABLE IF EXISTS `internship_task_major`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `internship_task_major` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `task_id` bigint NOT NULL COMMENT '????ID',
  `major_id` bigint NOT NULL COMMENT '??ID',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_major` (`task_id`,`major_id`),
  KEY `idx_major_task` (`major_id`,`task_id`)
) ENGINE=InnoDB AUTO_INCREMENT=60 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='???????';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `internship_task_student`
--

DROP TABLE IF EXISTS `internship_task_student`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `internship_task_student` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `task_id` bigint NOT NULL COMMENT '????ID',
  `student_id` bigint NOT NULL COMMENT '????ID',
  `student_number` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '??',
  `student_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '????',
  `class_id` bigint DEFAULT NULL COMMENT '??ID',
  `teacher_id` bigint DEFAULT NULL COMMENT '指导教师用户ID',
  `read_material_ids` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '已阅读安全资料ID集合',
  `read_material_count` int NOT NULL DEFAULT '0' COMMENT '已阅读资料篇数',
  `study_start_time` datetime DEFAULT NULL COMMENT '首次阅读安全资料时间',
  `study_complete_time` datetime DEFAULT NULL COMMENT '全部必读资料阅读完成时间',
  `safety_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NOT_STARTED' COMMENT '当前安全教育五阶段状态',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_student` (`task_id`,`student_id`),
  KEY `idx_student_task` (`student_id`,`task_id`),
  KEY `idx_task_teacher` (`task_id`,`teacher_id`)
) ENGINE=InnoDB AUTO_INCREMENT=498 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='???????????';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `internship_weekly_report`
--

DROP TABLE IF EXISTS `internship_weekly_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `internship_weekly_report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` bigint NOT NULL COMMENT '实习任务ID',
  `student_id` bigint NOT NULL COMMENT '学生用户ID',
  `teacher_id` bigint NOT NULL COMMENT '指导教师用户ID',
  `dept_id` bigint NOT NULL COMMENT '二级院系ID',
  `week_number` int NOT NULL COMMENT '周次序号 (第几周, 如1, 2, 3...)',
  `start_date` date NOT NULL COMMENT '本周起止开始日期',
  `end_date` date NOT NULL COMMENT '本周起止结束日期',
  `deadline_time` datetime NOT NULL COMMENT '本周截止提交时刻',
  `work_content` text COLLATE utf8mb4_unicode_ci COMMENT '本周工作内容 (WEEKLY-002, 提交时必填, 草稿允许为NULL)',
  `work_summary` text COLLATE utf8mb4_unicode_ci COMMENT '实习收获与体会 (WEEKLY-002, 提交时必填, 草稿允许为NULL)',
  `problem_encountered` text COLLATE utf8mb4_unicode_ci COMMENT '遇到的问题与思路 (WEEKLY-002, 提交时必填, 草稿允许为NULL)',
  `next_week_plan` text COLLATE utf8mb4_unicode_ci COMMENT '下周工作计划 (WEEKLY-002, 提交时必填, 草稿允许为NULL)',
  `attachment_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '成果或佐证附件URL (引用路径)',
  `version` int NOT NULL DEFAULT '1' COMMENT '修改版本号 (退回重提自增)',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '周报状态 (DRAFT:草稿, SUBMITTED:待批阅, REVIEWED:已批阅, RETURNED:已退回)',
  `is_overdue` tinyint NOT NULL DEFAULT '0' COMMENT '是否逾期提交 (0:按期, 1:逾期)',
  `overdue_days` int NOT NULL DEFAULT '0' COMMENT '逾期天数',
  `submit_time` datetime DEFAULT NULL COMMENT '正式提交时间',
  `score` decimal(5,2) DEFAULT NULL COMMENT '周报批阅得分 (百分制 0.00-100.00)',
  `review_comment` text COLLATE utf8mb4_unicode_ci COMMENT '导师指导评语/退回意见 (GUIDANCE-001)',
  `review_annotations` text COLLATE utf8mb4_unicode_ci COMMENT '逐项批注内容 (JSON格式)',
  `reviewer_id` bigint DEFAULT NULL COMMENT '实际批阅人用户ID',
  `review_time` datetime DEFAULT NULL COMMENT '批阅完成时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除 (0:正常, 1:删除)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_student_week` (`task_id`,`student_id`,`week_number`),
  KEY `idx_weekly_teacher_status` (`teacher_id`,`status`,`is_deleted`),
  KEY `idx_weekly_student_task` (`student_id`,`task_id`),
  KEY `idx_weekly_dept_status` (`dept_id`,`status`),
  KEY `idx_weekly_status_submit` (`status`,`submit_time`)
) ENGINE=InnoDB AUTO_INCREMENT=395 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生实习周报主表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `internship_weekly_report_history`
--

DROP TABLE IF EXISTS `internship_weekly_report_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `internship_weekly_report_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `report_id` bigint NOT NULL COMMENT '周报主表ID',
  `task_id` bigint NOT NULL COMMENT '实习任务ID',
  `student_id` bigint NOT NULL COMMENT '学生用户ID',
  `version` int NOT NULL COMMENT '本次流转对应的周报版本号',
  `action` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作动作 (SUBMIT:正式提交, APPROVE:批阅通过, RETURN:退回修改)',
  `operator_id` bigint NOT NULL COMMENT '操作人ID',
  `operator_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作人姓名',
  `operator_role` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作人角色',
  `return_reason` text COLLATE utf8mb4_unicode_ci COMMENT '退回修改原因 (退回时必填)',
  `score` decimal(5,2) DEFAULT NULL COMMENT '批阅得分 (若有)',
  `review_comment` text COLLATE utf8mb4_unicode_ci COMMENT '批语快照 (若有)',
  `snapshot_content` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '当时周报全文数据快照 (JSON格式)',
  `operate_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_history_report` (`report_id`,`version`),
  KEY `idx_history_student` (`student_id`,`task_id`)
) ENGINE=InnoDB AUTO_INCREMENT=145 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='周报流转与退回历史快照表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `material_version_history`
--

DROP TABLE IF EXISTS `material_version_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `material_version_history` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '鐗堟湰鍘嗗彶涓婚敭ID',
  `material_id` bigint unsigned NOT NULL COMMENT '鍏宠仈鏉愭枡涓昏〃ID',
  `task_id` bigint unsigned NOT NULL COMMENT '鎵?睘瀹炰範浠诲姟ID',
  `student_id` bigint unsigned NOT NULL COMMENT '瀛︾敓ID',
  `version` int unsigned NOT NULL COMMENT '蹇?収鐗堟湰鍙',
  `content_text` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '璇ョ増鏈?暱鏂囨湰姝ｆ枃蹇?収',
  `attachment_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '璇ョ増鏈?綈璇乁RL蹇?収',
  `file_name` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鏂囦欢鍚嶇О蹇?収',
  `submit_time` datetime NOT NULL COMMENT '璇ョ増鏈?彁鎶ユ椂闂',
  `audit_teacher_id` bigint unsigned DEFAULT NULL COMMENT '璇ョ増鏈?煡楠屾暀甯圛D',
  `audit_score` decimal(5,2) DEFAULT NULL COMMENT '璇ョ増鏈??璇勫垎鏁板揩鐓',
  `audit_comment` text COLLATE utf8mb4_unicode_ci COMMENT '璇ョ増鏈?暀甯堣瘎璇?揩鐓',
  `audit_time` datetime DEFAULT NULL COMMENT '璇ョ増鏈?煡楠屾椂闂',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鏌ラ獙缁堟?鐘舵?',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '蹇?収璁板綍鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_mat_hist_item` (`material_id`,`version`),
  KEY `idx_mat_hist_student` (`student_id`)
) ENGINE=InnoDB AUTO_INCREMENT=70 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='闃舵?鏉愭枡鍘嗗彶鐗堟湰蹇?収琛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `midterm_inspection`
--

DROP TABLE IF EXISTS `midterm_inspection`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `midterm_inspection` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '妫?煡璁板綍涓婚敭ID',
  `plan_id` bigint unsigned NOT NULL COMMENT '鎵?睘涓?湡妫?煡鏂规?ID',
  `task_id` bigint unsigned NOT NULL COMMENT '鎵?睘瀹炰範浠诲姟ID',
  `student_id` bigint unsigned NOT NULL COMMENT '琚??鏌ュ?鐢烮D',
  `teacher_id` bigint unsigned NOT NULL COMMENT '瀛︾敓鎸囧?鏁欏笀ID',
  `inspector_id` bigint unsigned NOT NULL COMMENT '鎵ц?鐫ｅ?妫?煡浜哄憳ID',
  `sampling_batch_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鎶芥牱鎵规?鍙',
  `inspection_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ONSITE' COMMENT '妫?煡褰㈠紡: ONSITE(鐜板満璧拌?), ONLINE(缃戠粶瑙嗛?杩炵嚎), PHONE(鐢佃瘽闂??)',
  `inspection_date` datetime NOT NULL COMMENT '瀹為檯妫?煡鎵ц?鏃堕棿',
  `company_situation` text COLLATE utf8mb4_unicode_ci COMMENT '瀹炰範鍗曚綅鐜??涓庡矖浣嶅尮閰嶈?瀵熸儏鍐',
  `student_performance` text COLLATE utf8mb4_unicode_ci COMMENT '瀛︾敓鍑哄嫟銆佸伐浣滀綔椋庝笌涓撲笟鎶?兘鎺屾彙鎯呭喌',
  `guidance_fulfillment` text COLLATE utf8mb4_unicode_ci COMMENT '鎸囧?鏁欏笀灞ヨ亴涓庡甫鏁欏彴璐︽牳鏌ユ儏鍐',
  `score` decimal(5,2) DEFAULT NULL COMMENT '鐫ｅ?璇勫垎 (0.00 ~ 100.00)',
  `attachment_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鐫ｅ?璧拌?鐜板満鐓х墖/绛惧埌鍑?瘉閾炬帴',
  `has_problem` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '鏄?惁鍙戠幇绐佸嚭闂??: 0-鏃犻棶棰? 1-瀛樺湪闂??闇?暣鏀',
  `problem_desc` text COLLATE utf8mb4_unicode_ci COMMENT '妫?煡鍙戠幇鐨勯棶棰樻槑缁嗘弿杩',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INSPECTED' COMMENT '璁板綍鐘舵?: INSPECTED(宸叉?鏌ュ悎鏍?, PENDING_RECTIFY(寰呮暣鏀?, RECTIFIED(宸叉暣鏀归棴鐜?',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎: 0-姝ｅ父, 1-鍒犻櫎',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_student` (`plan_id`,`student_id`),
  KEY `idx_inspect_task_student` (`task_id`,`student_id`),
  KEY `idx_inspect_teacher` (`teacher_id`),
  KEY `idx_inspect_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=3093 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='涓?湡妫?煡鎶芥煡涓庣潱瀵艰?褰曟槑缁嗚〃';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `midterm_inspection_plan`
--

DROP TABLE IF EXISTS `midterm_inspection_plan`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `midterm_inspection_plan` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '鏂规?涓婚敭ID',
  `plan_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '妫?煡鏂规?鍚嶇О',
  `task_id` bigint unsigned NOT NULL COMMENT '鎵?睘瀹炰範浠诲姟ID',
  `dept_id` bigint unsigned NOT NULL COMMENT '鍙戣捣浜岀骇闄㈢郴ID',
  `sampling_mode` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'RANDOM_RATIO' COMMENT '鎶芥牱妯″紡: RANDOM_RATIO(鎸夋瘮渚嬮殢鏈?, CLASS_SELECT(鏁村缓鍒剁彮绾?',
  `sampling_ratio` decimal(5,2) DEFAULT NULL COMMENT '鎶芥牱姣斾緥鐧惧垎姣?濡?20.00)',
  `start_date` date NOT NULL COMMENT '妫?煡鍚?姩鏃ユ湡',
  `end_date` date NOT NULL COMMENT '妫?煡鎴??鏃ユ湡',
  `expert_group` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '妫?煡涓撳?缁勬垚鍛樹俊鎭',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鏂规?缂栧埗瑕佹眰鍙婃寚瀵艰?鏄',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '鏂规?鐘舵?: DRAFT(鑽夌?), PUBLISHED(宸插彂甯?, COMPLETED(宸插畬鎴?',
  `created_by` bigint unsigned NOT NULL COMMENT '鍒涘缓浜篒D',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎: 0-姝ｅ父, 1-鍒犻櫎',
  PRIMARY KEY (`id`),
  KEY `idx_plan_task_dept` (`task_id`,`dept_id`),
  KEY `idx_plan_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=2047 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='涓?湡妫?煡鏂规?涓昏〃';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `midterm_rectification`
--

DROP TABLE IF EXISTS `midterm_rectification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `midterm_rectification` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '鏁存敼璁板綍涓婚敭ID',
  `inspection_id` bigint unsigned NOT NULL COMMENT '鍏宠仈涓?湡妫?煡璁板綍ID',
  `task_id` bigint unsigned NOT NULL COMMENT '鎵?睘瀹炰範浠诲姟ID',
  `student_id` bigint unsigned NOT NULL COMMENT '琚?暣鏀瑰?鐢烮D',
  `responsible_user_id` bigint unsigned NOT NULL COMMENT '鏁存敼涓昏?璐ｄ换浜篒D',
  `rectify_requirements` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '闄愭湡鏁存敼鍏蜂綋瑕佹眰涓庢敼杩涙寚鏍',
  `deadline_date` date NOT NULL COMMENT '鏁存敼瀹屾垚鎴??鏃ユ湡',
  `student_explanation` text COLLATE utf8mb4_unicode_ci COMMENT '瀛︾敓鏁存敼鎺?柦钀藉疄璇存槑涓庢繁鍒绘?璁',
  `evidence_attachment_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鏁存敼鍚庝綈璇佸嚟鎹?綉缁滈摼鎺?(鍥剧墖/PDF)',
  `submit_time` datetime DEFAULT NULL COMMENT '鏁存敼鎻愪氦鏃堕棿',
  `review_teacher_id` bigint unsigned DEFAULT NULL COMMENT '澶嶆牳鏁欏笀ID',
  `review_comment` text COLLATE utf8mb4_unicode_ci COMMENT '鏁欏笀澶嶆牳璇勪环涓庢暣鏀规垚鏁堟壒璇',
  `review_time` datetime DEFAULT NULL COMMENT '鏁欏笀澶嶆牳鏃堕棿',
  `close_dept_user_id` bigint unsigned DEFAULT NULL COMMENT '闄㈢郴缁堝?闂?幆浜篒D',
  `close_time` datetime DEFAULT NULL COMMENT '鏁存敼缁堝?闂?幆鏃堕棿',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING_SUBMIT' COMMENT '鏁存敼鐘舵?: PENDING_SUBMIT(寰呮彁浜ゆ暣鏀?, PENDING_REVIEW(寰呮暀甯堝?鏍?, REJECTED(澶嶆牳涓嶅悎鏍奸?鍥?, CLOSED(宸查攢鍙烽棴鐜?',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎: 0-姝ｅ父, 1-鍒犻櫎',
  PRIMARY KEY (`id`),
  KEY `idx_rectify_inspect` (`inspection_id`),
  KEY `idx_rectify_task_student` (`task_id`,`student_id`),
  KEY `idx_rectify_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=67 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='涓?湡妫?煡闄愭湡鏁存敼閫氱煡涓庤惤瀹炶〃';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `safety_commitment_sign`
--

DROP TABLE IF EXISTS `safety_commitment_sign`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `safety_commitment_sign` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `task_id` bigint NOT NULL COMMENT '????ID',
  `student_id` bigint NOT NULL COMMENT '????ID',
  `commitment_text` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '?????????',
  `is_signed` tinyint NOT NULL DEFAULT '1' COMMENT '???? (1:???, 0:???)',
  `sign_ip` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '?????IP',
  `sign_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `insurance_file_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '??????????????',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_student_sign` (`task_id`,`student_id`)
) ENGINE=InnoDB AUTO_INCREMENT=286 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='?????????????';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `safety_exam_answer_detail`
--

DROP TABLE IF EXISTS `safety_exam_answer_detail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `safety_exam_answer_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `attempt_id` bigint NOT NULL COMMENT '????ID',
  `question_id` bigint NOT NULL COMMENT '??ID',
  `student_answer` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '??????',
  `is_correct` tinyint NOT NULL DEFAULT '0' COMMENT '???? (1:??, 0:??)',
  `score_obtained` decimal(5,2) NOT NULL DEFAULT '0.00' COMMENT '????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`),
  KEY `idx_detail_attempt` (`attempt_id`)
) ENGINE=InnoDB AUTO_INCREMENT=151 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='???????????';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `safety_exam_attempt`
--

DROP TABLE IF EXISTS `safety_exam_attempt`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `safety_exam_attempt` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `task_id` bigint NOT NULL COMMENT '????ID',
  `student_id` bigint NOT NULL COMMENT '????ID',
  `attempt_no` int NOT NULL DEFAULT '1' COMMENT '?????',
  `total_score` decimal(5,2) NOT NULL DEFAULT '0.00' COMMENT '????',
  `is_passed` tinyint NOT NULL DEFAULT '0' COMMENT '?????? (1:?, 0:?)',
  `start_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `submit_time` datetime DEFAULT NULL COMMENT '????',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`),
  KEY `idx_exam_student_task` (`student_id`,`task_id`,`attempt_no`)
) ENGINE=InnoDB AUTO_INCREMENT=198 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='?????????';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `safety_material_item`
--

DROP TABLE IF EXISTS `safety_material_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `safety_material_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `task_id` bigint DEFAULT NULL COMMENT '????ID (NULL??????? SAFE-001)',
  `title` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '????',
  `content_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TEXT' COMMENT '???? (TEXT:??, PDF:??, VIDEO:??, URL:??)',
  `content_body` longtext COLLATE utf8mb4_unicode_ci COMMENT '??????',
  `file_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '??????',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '????',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '?? (1:??, 0:??)',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`),
  KEY `idx_safety_mat_task` (`task_id`,`status`,`is_deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='?????????';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `safety_test_question`
--

DROP TABLE IF EXISTS `safety_test_question`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `safety_test_question` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '??ID',
  `task_id` bigint DEFAULT NULL COMMENT '????ID (NULL???????)',
  `question_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '?? (SINGLE_CHOICE:??, MULTIPLE_CHOICE:??, JUDGMENT:??)',
  `stem` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '????',
  `options` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '???? (JSON??)',
  `correct_answer` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '???? (? "A" ? "A,B" ? "TRUE")',
  `score` int NOT NULL DEFAULT '10' COMMENT '????',
  `analysis` text COLLATE utf8mb4_unicode_ci COMMENT '???????????',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '????',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '?? (1:??, 0:??)',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '????',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '????',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '????',
  PRIMARY KEY (`id`),
  KEY `idx_safety_quest_task` (`task_id`,`status`,`is_deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=999 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='???????';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `score_audit_history`
--

DROP TABLE IF EXISTS `score_audit_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `score_audit_history` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `score_id` bigint unsigned NOT NULL COMMENT '鍏宠仈鎴愮哗ID',
  `task_id` bigint unsigned NOT NULL COMMENT '浠诲姟ID',
  `student_id` bigint unsigned NOT NULL COMMENT '瀛︾敓ID',
  `action` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鍔ㄤ綔: APPEAL_APPLY, APPEAL_PASS, APPEAL_REJECT, SPECIAL_MODIFY',
  `appeal_reason` text COLLATE utf8mb4_unicode_ci COMMENT '鐢宠瘔鐞嗙敱',
  `appeal_attachment_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '浣愯瘉閾炬帴',
  `old_score_snapshot` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '璋冨垎鍓嶅揩鐓?SON',
  `new_score_snapshot` text COLLATE utf8mb4_unicode_ci COMMENT '璋冨垎鍚庡揩鐓?SON',
  `audit_user_id` bigint unsigned NOT NULL COMMENT '澶勭悊浜篒D',
  `audit_user_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '澶勭悊浜哄?鍚',
  `audit_comment` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '璋冨垎渚濇嵁璇存槑/椹冲洖鐞嗙敱',
  `approval_doc_no` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '绾夸笅璋冨垎绾㈠ご鎵规枃澶囨?鍙',
  `operate_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '澶勭悊鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_score_hist_score` (`score_id`),
  KEY `idx_score_hist_student` (`student_id`)
) ENGINE=InnoDB AUTO_INCREMENT=56 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鎴愮哗寮傝?鐢宠瘔涓庤皟鍒嗗?鎵瑰巻鍙茶〃';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `score_summary`
--

DROP TABLE IF EXISTS `score_summary`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `score_summary` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '鎴愮哗涓婚敭ID',
  `task_id` bigint unsigned NOT NULL COMMENT '鎵?睘瀹炰範浠诲姟ID',
  `student_id` bigint unsigned NOT NULL COMMENT '瀛︾敓ID',
  `teacher_id` bigint unsigned NOT NULL COMMENT '褰曞叆鎸囧?鏁欏笀ID',
  `dept_id` bigint unsigned NOT NULL COMMENT '鎵?睘闄㈢郴ID',
  `enterprise_score` decimal(5,2) DEFAULT NULL COMMENT '浜旂淮涔嬩竴: 浼佷笟閴村畾鍒?(0-100)锛屾湭褰曞叆涓篘ULL',
  `process_score` decimal(5,2) DEFAULT NULL COMMENT '浜旂淮涔嬩簩: 杩囩▼琛ㄧ幇鍒?(0-100)锛屾湭褰曞叆涓篘ULL',
  `weekly_score` decimal(5,2) DEFAULT NULL COMMENT '浜旂淮涔嬩笁: 闃舵?6鍛ㄦ姤缁煎悎鍧囧垎 (0-100)锛屾湭姹囩畻涓篘ULL',
  `material_score` decimal(5,2) DEFAULT NULL COMMENT '浜旂淮涔嬪洓: 杩囩▼鏉愭枡鑰冭瘎鍒?(0-100)锛屾湭璇勫畾涓篘ULL',
  `summary_score` decimal(5,2) DEFAULT NULL COMMENT '浜旂淮涔嬩簲: 瀹炰範鎬荤粨鎶ュ憡鍒?(0-100)锛屾湭璇勫畾涓篘ULL',
  `final_score` decimal(5,2) DEFAULT NULL COMMENT '鍔犳潈璁＄畻鏈?粓鎬诲垎 (0.00-100.00)锛屾湭瀹屾垚姹囩畻涓篘ULL',
  `score_level` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '浜旂骇鍒剁瓑绗? EXCELLENT, GOOD, MEDIUM, PASS, FAIL锛屾湭瀹氫负NULL',
  `grade_rule_snapshot_json` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '璇勫畾鏃堕噰鐢ㄧ殑浠诲姟绾ф垨鍏ㄥ眬绛夌?鍒掑垎瑙勫垯蹇?収JSON (鍥哄寲鍚庝笉鍙?彉)',
  `evaluation_comment` text COLLATE utf8mb4_unicode_ci COMMENT '鏁欏笀缁煎悎瀹炰範鎬昏瘎璇',
  `enterprise_evaluation_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '浼佷笟閴村畾琛ㄧ洊绔犳壂鎻忎欢浣愯瘉閾炬帴',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '鎴愮哗鐘舵?: DRAFT(鑽夌?), PENDING_AUDIT(寰呴櫌绯诲?鏍?, PUBLICITY(鍏?ず涓?, PUBLISHED(宸叉?寮忓彂甯?',
  `publicity_start_time` datetime DEFAULT NULL COMMENT '鍏?ず寮??鏃堕棿',
  `publicity_end_time` datetime DEFAULT NULL COMMENT '鍏?ず鎴??鏃堕棿',
  `confirmed_teacher_time` datetime DEFAULT NULL COMMENT '鏁欏笀鎻愪氦纭??鏃堕棿',
  `audited_dept_user_id` bigint unsigned DEFAULT NULL COMMENT '闄㈢郴瀹℃牳浜篒D',
  `audited_dept_time` datetime DEFAULT NULL COMMENT '闄㈢郴瀹℃牳鍙戝竷鏃堕棿',
  `version` int unsigned NOT NULL DEFAULT '1' COMMENT '鐗堟湰涔愯?閿',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎: 0-姝ｅ父, 1-鍒犻櫎',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_score_task_student` (`task_id`,`student_id`),
  KEY `idx_score_teacher` (`teacher_id`),
  KEY `idx_score_dept_status` (`dept_id`,`status`),
  KEY `idx_score_level` (`score_level`)
) ENGINE=InnoDB AUTO_INCREMENT=4106 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='瀹炰範鎴愮哗浜旂淮缁煎悎璇勫畾涓昏〃';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `student_material_item`
--

DROP TABLE IF EXISTS `student_material_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_material_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '鏉愭枡涓婚敭ID',
  `task_id` bigint unsigned NOT NULL COMMENT '鎵?睘瀹炰範浠诲姟ID',
  `student_id` bigint unsigned NOT NULL COMMENT '瀛︾敓ID',
  `material_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鏉愭枡缂栫爜: TRIPARTITE_AGREEMENT, EMPLOYMENT_NOTICE, SAFETY_TRAINING_RECORD, MIDTERM_SUMMARY, SUMMARY_REPORT',
  `material_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鏉愭枡鏄剧ず鍚嶇О',
  `material_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'VOUCHER_FILE' COMMENT '鏉愭枡褰㈡?: VOUCHER_FILE(鍑?嵁URL), REPORT_TEXT(闀挎枃鏈?, HYBRID(鍥炬枃)',
  `content_text` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '闀挎枃鏈??鏂囧唴瀹?(鎬荤粨鎶ュ憡/涓?湡鎬荤粨姝ｆ枃)',
  `attachment_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鍑?嵁浣愯瘉缃戠粶URL (jpg/png/pdf)',
  `file_name` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鍘熷?鏂囦欢鍚嶇О',
  `file_size` bigint unsigned DEFAULT NULL COMMENT '鏂囦欢瀛楄妭澶у皬',
  `version` int unsigned NOT NULL DEFAULT '1' COMMENT '褰撳墠鎻愭姤鐗堟湰鍙',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UNSUBMITTED' COMMENT '鐘舵?: UNSUBMITTED(鏈?彁浜?, SUBMITTED(宸叉彁浜?, APPROVED(鏌ラ獙鍚堟牸), RETURNED(閫?洖閲嶄慨)',
  `submit_time` datetime DEFAULT NULL COMMENT '鏈?柊鎻愪氦鏃堕棿',
  `audit_teacher_id` bigint unsigned DEFAULT NULL COMMENT '鏌ラ獙瀹℃牳鎸囧?鏁欏笀ID',
  `audit_score` decimal(5,2) DEFAULT NULL COMMENT '鏉愭枡鏌ラ獙鑰冭瘎鍒嗘暟 (0.00 ~ 100.00)',
  `audit_comment` text COLLATE utf8mb4_unicode_ci COMMENT '鏁欏笀鏌ラ獙鎰忚?鎴栭?鍥炲師鍥',
  `audit_time` datetime DEFAULT NULL COMMENT '鏁欏笀鏌ラ獙鏃堕棿',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎: 0-姝ｅ父, 1-鍒犻櫎',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_student_material` (`task_id`,`student_id`,`material_code`),
  KEY `idx_mat_teacher_status` (`audit_teacher_id`,`status`),
  KEY `idx_mat_student` (`student_id`)
) ENGINE=InnoDB AUTO_INCREMENT=234 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='闃舵?鏉愭枡涓庢彁鎶ユ槑缁嗕富琛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_backup_record`
--

DROP TABLE IF EXISTS `sys_backup_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_backup_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '澶囦唤ID',
  `backup_file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鍙楁帶鐩稿?鏂囦欢鍚?(濡?internship_db_backup_xxx.sql)',
  `file_size_bytes` bigint NOT NULL COMMENT '鏂囦欢瀛楄妭澶у皬',
  `table_count` int NOT NULL COMMENT '鍖呭惈鏁版嵁琛ㄦ暟',
  `sha256_digest` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '澶囦唤鏂囦欢SHA-256瀹屾暣鎬ф牎楠屾憳瑕',
  `is_locked` tinyint NOT NULL DEFAULT '0' COMMENT '閿佸畾淇濇姢鏍囪瘑 (0-鍙?竻鐞? 1-閿佸畾闃插垹鍩虹嚎)',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SUCCESS' COMMENT '鐘舵? (SUCCESS-鎴愬姛, FAILED-澶辫触)',
  `error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '澶辫触閿欒?淇℃伅',
  `operator_id` bigint DEFAULT NULL COMMENT '鎿嶄綔浜哄憳ID (0涓哄畾鏃惰Е鍙?',
  `backup_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '澶囦唤瀹屾垚鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_backup_time` (`backup_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鏁版嵁搴撳彈鎺у?浠借?褰曡〃';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_config`
--

DROP TABLE IF EXISTS `sys_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '鍙傛暟涓婚敭ID',
  `config_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鍙傛暟鍚嶇О',
  `config_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鍙傛暟閿?悕 (鍏ㄥ眬鍞?竴)',
  `config_value` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鍙傛暟閿??',
  `is_system` tinyint NOT NULL DEFAULT '1' COMMENT '绯荤粺鍐呯疆 (0-鍚? 1-鏄?笉鍙?垹)',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '澶囨敞璇存槑',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鍒涘缓鑰',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updated_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鏇存柊鑰',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎鏍囪瘑 (0-鏈?垹闄? 1-宸插垹闄?',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`,`is_deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='绯荤粺鍏ㄥ眬杩愮淮鍙傛暟閰嶇疆琛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_job`
--

DROP TABLE IF EXISTS `sys_job`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_job` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '浠诲姟ID',
  `job_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鍙楅檺浠诲姟缂栫爜鐧藉悕鍗?(WARN_SCAN_JOB / ARCHIVE_EXPIRE_RECLOCK_JOB / BACKUP_CLEANUP_JOB)',
  `job_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '浠诲姟鍚嶇О',
  `cron_expression` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Cron鎵ц?琛ㄨ揪寮',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '鐘舵? (0-鏆傚仠, 1-姝ｅ父)',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '澶囨敞璇存槑',
  `created_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鍒涘缓鑰',
  `created_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updated_by` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鏇存柊鑰',
  `updated_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎鏍囪瘑 (0-鏈?垹闄? 1-宸插垹闄?',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_code` (`job_code`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='绯荤粺鍙楅檺瀹氭椂浠诲姟閰嶇疆琛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_notice`
--

DROP TABLE IF EXISTS `sys_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '鍏?憡ID',
  `dedup_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '涓氬姟闃查噸閿?(浜哄伐鎵规?鍙锋垨浜嬩欢鍞?竴鏍囪瘑)',
  `notice_title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鍏?憡鏍囬?',
  `notice_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '绫诲瀷 (NOTICE-閫氱煡, ANNOUNCE-鍏?憡)',
  `notice_content` longtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鍏?憡姝ｆ枃 (鏈嶅姟绔?Jsoup 鐧藉悕鍗曞噣鍖栧瓨鍌?',
  `target_scope` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ALL' COMMENT '鍙戝竷鑼冨洿 (ALL-鍏ㄦ牎, DEPT-鏈?櫌绯?',
  `target_dept_id` bigint DEFAULT NULL COMMENT '闄愬畾闄㈢郴ID',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '鐘舵? (0-鍏抽棴, 1-姝ｅ父鍙戝竷)',
  `publisher_id` bigint NOT NULL COMMENT '鍙戝竷浜篒D',
  `publisher_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鍙戝竷浜哄?鍚',
  `publish_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍙戝竷鏃堕棿',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎鏍囪瘑 (0-鏈?垹闄? 1-宸插垹闄?',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_dedup` (`dedup_key`,`is_deleted`),
  KEY `idx_scope` (`target_scope`,`target_dept_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鍏ㄥ眬鏁欏?閫氱煡鍏?憡琛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_notice_read`
--

DROP TABLE IF EXISTS `sys_notice_read`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_notice_read` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `notice_id` bigint NOT NULL COMMENT '鍏?憡ID',
  `user_id` bigint NOT NULL COMMENT '闃呰?鐢ㄦ埛ID',
  `read_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '闃呰?鏃堕棿鎴',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_user` (`notice_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='閫氱煡鍏?憡鐢ㄦ埛闃呰?鐘舵?璁板綍琛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_operation_log`
--

DROP TABLE IF EXISTS `sys_operation_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `title` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '妯″潡鏍囬?/鎿嶄綔鍚嶇О',
  `business_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '涓氬姟绫诲瀷 (LOGIN, LOGOUT, QUERY, INSERT, UPDATE, DELETE, DENIED)',
  `method` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '璋冪敤鏂规硶鍚嶇О',
  `request_method` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'HTTP璇锋眰鏂瑰紡',
  `operator_id` bigint DEFAULT NULL COMMENT '鎿嶄綔浜哄憳ID',
  `operator_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鎿嶄綔浜哄憳濮撳悕/璐﹀彿',
  `oper_url` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '璇锋眰URL',
  `oper_ip` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '涓绘満IP鍦板潃',
  `oper_param` text COLLATE utf8mb4_unicode_ci COMMENT '璇锋眰鍙傛暟 (鑴辨晱鍚?',
  `json_result` text COLLATE utf8mb4_unicode_ci COMMENT '鍝嶅簲缁撴灉',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '鎿嶄綔鐘舵? (1:鎴愬姛, 0:澶辫触)',
  `error_msg` text COLLATE utf8mb4_unicode_ci COMMENT '閿欒?淇℃伅',
  `oper_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鎿嶄綔鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_log_user_time` (`operator_id`,`oper_time`),
  KEY `idx_log_type_status` (`business_type`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=8004 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鎿嶄綔涓庡畨鍏ㄥ?璁℃棩蹇楄〃';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `role_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '瑙掕壊鏍囪瘑',
  `role_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '瑙掕壊鍚嶇О',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '瑙掕壊鎻忚堪',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎 (0:姝ｅ父, 1:鍒犻櫎)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='瑙掕壊瀹氫箟琛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `username` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鐧诲綍鐧诲綍鍚?璐﹀彿',
  `password` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'BCrypt鍔犲瘑瀵嗙爜',
  `real_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鐪熷疄濮撳悕',
  `user_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鐢ㄦ埛绫诲瀷 (STUDENT, TEACHER, DEPT_ADMIN, SYS_ADMIN)',
  `user_number` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '瀛﹀彿/鏁欏伐鍙',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鑱旂郴鐢佃瘽',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鑱旂郴閭??',
  `dept_id` bigint DEFAULT NULL COMMENT '鎵?睘瀛﹂櫌ID',
  `major_id` bigint DEFAULT NULL COMMENT '鎵?睘涓撲笟ID (瀛︾敓涓撴湁)',
  `class_id` bigint DEFAULT NULL COMMENT '鎵?睘鐝?骇ID (瀛︾敓涓撴湁)',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '璐﹀彿鐘舵? (1:姝ｅ父, 0:绂佺敤)',
  `token_version` bigint NOT NULL DEFAULT '1' COMMENT 'Token鐗堟湰鍙?(娉ㄩ攢/鏀瑰瘑鏃惰嚜澧炰娇鏃?oken澶辨晥)',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎 (0:姝ｅ父, 1:鍒犻櫎)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_user_number` (`user_number`),
  KEY `idx_user_dept` (`dept_id`),
  KEY `idx_user_class` (`class_id`),
  KEY `idx_user_type_status` (`user_type`,`status`,`is_deleted`)
) ENGINE=InnoDB AUTO_INCREMENT=1302 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='绯荤粺鐢ㄦ埛琛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭ID',
  `user_id` bigint NOT NULL COMMENT '鐢ㄦ埛ID',
  `role_id` bigint NOT NULL COMMENT '瑙掕壊ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`),
  KEY `idx_role_user` (`role_id`,`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=194 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鐢ㄦ埛瑙掕壊鍏宠仈琛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `warn_process_history`
--

DROP TABLE IF EXISTS `warn_process_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `warn_process_history` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '鍘嗗彶璁板綍涓婚敭ID',
  `ticket_id` bigint unsigned NOT NULL COMMENT '鍏宠仈棰勮?宸ュ崟ID',
  `action` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鍔ㄤ綔绫诲瀷',
  `operator_id` bigint unsigned NOT NULL COMMENT '鎿嶄綔浜篒D',
  `operator_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鎿嶄綔浜哄?鍚',
  `operator_role` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鎿嶄綔浜鸿?鑹',
  `content_remark` text COLLATE utf8mb4_unicode_ci COMMENT '娴佽浆璇存槑',
  `attachment_url` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鏀?拺鍑?瘉閾炬帴',
  `operate_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '娴佽浆鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_warn_hist_ticket` (`ticket_id`),
  KEY `idx_warn_hist_operator` (`operator_id`)
) ENGINE=InnoDB AUTO_INCREMENT=229 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='寮傚父棰勮?娴佽浆鍘嗗彶琛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `warn_rule_config`
--

DROP TABLE IF EXISTS `warn_rule_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `warn_rule_config` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '瑙勫垯涓婚敭ID',
  `rule_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '棰勮?瑙勫垯鍞?竴涓氬姟鏍囪瘑缂栫爜 (濡?WARN_01)',
  `rule_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '瑙勫垯鍚嶇О',
  `anomaly_category` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '绠＄悊寮傚父鍒嗙被: SAFETY, SCHEDULE, GUIDANCE, QUALITY',
  `warn_level` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'YELLOW' COMMENT '棰勮?绾у埆: YELLOW, ORANGE, RED',
  `threshold_params_json` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '鍔ㄦ?鍒ゅ畾闃堝?鍙傛暟JSON鏍煎紡',
  `dispatched_role` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TEACHER' COMMENT '榛樿?娲惧崟澶勭疆璐ｄ换瑙掕壊: TEACHER, DEPT_ADMIN',
  `handling_timeout_days` int unsigned NOT NULL DEFAULT '3' COMMENT '澶勭疆瓒呮椂澶╂暟',
  `is_enabled` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '鏄?惁鍚?敤: 0-鍋滅敤, 1-鍚?敤',
  `version` int unsigned NOT NULL DEFAULT '1' COMMENT '瑙勫垯鐗堟湰鍙',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '瑙勫垯鍒ゅ畾璇存槑涓庣?鐞嗕緷鎹',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎: 0-姝ｅ父, 1-鍒犻櫎',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_code` (`rule_code`),
  KEY `idx_rule_category_enabled` (`anomaly_category`,`is_enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鍏ㄥ眬寮傚父棰勮?瑙勫垯閰嶇疆琛';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `warn_ticket`
--

DROP TABLE IF EXISTS `warn_ticket`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `warn_ticket` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '棰勮?宸ュ崟涓婚敭ID',
  `ticket_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '棰勮?宸ュ崟娴佹按鍙?(WT+骞存湀鏃?8浣嶅簭鍒?',
  `task_id` bigint unsigned NOT NULL COMMENT '鎵?睘瀹炰範浠诲姟ID',
  `student_id` bigint unsigned NOT NULL COMMENT '娑夊強瀛︾敓ID',
  `teacher_id` bigint unsigned DEFAULT NULL COMMENT '鍏宠仈鎸囧?鏁欏笀ID',
  `dept_id` bigint unsigned NOT NULL COMMENT '鎵?睘浜岀骇闄㈢郴ID',
  `rule_id` bigint unsigned NOT NULL COMMENT '鍛戒腑棰勮?瑙勫垯ID',
  `rule_version` int unsigned NOT NULL COMMENT '鍛戒腑鏃惰?鍒欑増鏈?揩鐓',
  `warn_level` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '棰勮?绾у埆蹇?収: YELLOW, ORANGE, RED',
  `warn_title` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '棰勮?姒傝?鏍囬?',
  `evidence_snapshot_json` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '瑙﹀彂鏃跺浐鍖栫殑鍘熷?涓氬姟鏁版嵁涓庤瘉鎹?揩鐓?SON',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TRIGGERED' COMMENT '宸ュ崟涓荤姸鎬? TRIGGERED, DISPATCHED, PROCESSING, PENDING_REVIEW, CLOSED, FALSE_ALARM_CLOSED',
  `is_upgraded` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '鏄?惁宸插崌绾ц嚦闄㈢郴: 0-鏈?崌绾? 1-宸插崌绾',
  `upgraded_time` datetime DEFAULT NULL COMMENT '鍗囩骇鏃堕棿鎴',
  `upgrade_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鍗囩骇鍘熷洜闄堣堪',
  `current_assignee_id` bigint unsigned NOT NULL COMMENT '褰撳墠璐ｄ换浜篒D',
  `current_assignee_role` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '褰撳墠璐ｄ换浜鸿?鑹? TEACHER, DEPT_ADMIN',
  `dedup_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '姘镐箙鍘婚噸鐗瑰緛鍝堝笇鎸囩汗 (姘镐笉缃?┖)',
  `active_dedup_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '娲诲姩鍘婚噸閿?(娲诲姩涓?攣瀹氾紝缁堟?鍏抽棴缃甆ULL閲婃斁)',
  `student_feedback` text COLLATE utf8mb4_unicode_ci COMMENT '瀛︾敓濉?啓鐨勭敵杈╂儏鍐佃?鏄',
  `student_feedback_time` datetime DEFAULT NULL COMMENT '瀛︾敓鎻愪氦鐢宠京鏃堕棿',
  `teacher_investigation` text COLLATE utf8mb4_unicode_ci COMMENT '鏁欏笀璋冩煡鏍稿疄闄堣堪鎴栬?鎶ュ垽瀹氭姤鍛',
  `handling_measures` text COLLATE utf8mb4_unicode_ci COMMENT '閲囧彇鐨勫共棰勬帾鏂戒笌澶勭悊缁撴灉璁板綍',
  `closed_time` datetime DEFAULT NULL COMMENT '鏈?粓闂?幆閿?彿鏃堕棿',
  `closed_by` bigint unsigned DEFAULT NULL COMMENT '鏈?粓鍏抽棴瀹℃牳浜篒D',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `is_deleted` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎: 0-姝ｅ父, 1-鍒犻櫎',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ticket_no` (`ticket_no`),
  UNIQUE KEY `uk_active_dedup` (`active_dedup_key`),
  KEY `idx_ticket_task_student` (`task_id`,`student_id`),
  KEY `idx_ticket_assignee` (`current_assignee_id`,`status`),
  KEY `idx_ticket_dept_level` (`dept_id`,`warn_level`,`status`),
  KEY `idx_ticket_dedup_history` (`dedup_key`)
) ENGINE=InnoDB AUTO_INCREMENT=301 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='寮傚父棰勮?宸ュ崟涓昏〃';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-23  1:57:53

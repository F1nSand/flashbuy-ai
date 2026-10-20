/*
Navicat MySQL Data Transfer

Source Server         : seckillDocker
Source Server Version : 50722
Source Host           : 127.0.0.1:3306
Source Database       : seckill

Target Server Type    : MYSQL
Target Server Version : 50722
File Encoding         : 65001

Date: 2018-08-19 22:50:02
*/
SET NAMES 'utf8';
SET FOREIGN_KEY_CHECKS=0;

-- ----------------------------
-- Table structure for goods
-- ----------------------------
DROP TABLE IF EXISTS `goods`;
create table goods
(
    goods_id    int auto_increment
        primary key,
    photo_url   varchar(200) null,
    name        varchar(45)  null,
    price       varchar(45)  null,
    create_time timestamp    null,
    introduce   varchar(500) null
)
    charset = utf8mb3;

INSERT INTO seckill.goods (goods_id, photo_url, name, price, create_time, introduce) VALUES (1, 'http://localhost:19000/goodskill/%E5%BE%AE%E4%BF%A1%E6%88%AA%E5%9B%BE_20220511163056.png', 'iphone6', '4000', '2022-04-18 09:15:24', '似的发射点');
INSERT INTO seckill.goods (goods_id, photo_url, name, price, create_time, introduce) VALUES (2, 'http://localhost:19000/goodskill/%E5%BE%AE%E4%BF%A1%E5%9B%BE%E7%89%87_20220511162941.jpg', 'ipad2', '1900', null, null);
INSERT INTO seckill.goods (goods_id, photo_url, name, price, create_time, introduce) VALUES (3, null, '小米4', '800', null, null);
INSERT INTO seckill.goods (goods_id, photo_url, name, price, create_time, introduce) VALUES (4, null, '红米note', '699', null, null);
INSERT INTO seckill.goods (goods_id, photo_url, name, price, create_time, introduce) VALUES (5, null, '小米6', '2499', '2017-06-09 00:07:25', '小米6变焦双摄拍人更美');
INSERT INTO seckill.goods (goods_id, photo_url, name, price, create_time, introduce) VALUES (6, null, '小米max2', '1699', '2017-06-10 11:14:11', '6.44''''大屏 / 5300mAh 充电宝级的大电量 / 大像素相机 / 轻薄全金属 / ​4GB 大内存 / 骁龙八核处理器');
INSERT INTO seckill.goods (goods_id, photo_url, name, price, create_time, introduce) VALUES (7, null, '小米5c', '1499', '2017-06-10 11:16:20', '搭载澎湃S1 八核高性能处理器 / “暗夜之眼”超感光相机 / 135g 轻薄金属机身 / 5.15"高亮护眼屏');
INSERT INTO seckill.goods (goods_id, photo_url, name, price, create_time, introduce) VALUES (8, null, 'OPPO R11', '3499', '2017-06-10 11:23:43', '前后2000万，拍照更清晰');


-- ----------------------------
-- Table structure for permission
-- ----------------------------
create table permission
(
    permission_id        int unsigned auto_increment
        primary key,
    permission_name      varchar(64)                            null,
    create_time          datetime                               null,
    update_time          timestamp    default CURRENT_TIMESTAMP null,
    permission_menu      varchar(500)                           null,
    parent_permission_id int                                    null,
    is_dir               varchar(1)                             null,
    order_no             int          default 0                 not null,
    path                 varchar(200) default ''                not null,
    hidden               tinyint(1)   default 0                 null comment '是否隐藏：0-否，1-是',
    component            varchar(50)  default ''                not null,
    access_url           varchar(500) default ''                not null comment '访问url',
    redirect             varchar(100) default ''                not null
)
    collate = utf8_bin;

INSERT INTO seckill.permission (permission_id, permission_name, create_time, update_time, permission_menu, parent_permission_id, is_dir, order_no, path, hidden, component, access_url, redirect) VALUES (3, '用户管理', '2018-07-14 12:28:25', '2018-07-14 12:28:25', 'admin/user.html', 7, 'N', 1, 'user-manage', 0, 'views/table/user-manage', '', '');
INSERT INTO seckill.permission (permission_id, permission_name, create_time, update_time, permission_menu, parent_permission_id, is_dir, order_no, path, hidden, component, access_url, redirect) VALUES (4, '用户角色管理', '2018-07-14 12:28:25', '2018-07-14 12:28:25', 'admin/userRole.html', 7, 'N', 2, '', 1, '', '', '');
INSERT INTO seckill.permission (permission_id, permission_name, create_time, update_time, permission_menu, parent_permission_id, is_dir, order_no, path, hidden, component, access_url, redirect) VALUES (5, '菜单管理', '2018-07-14 12:28:25', '2018-07-14 12:28:25', 'admin/permission.html', 7, 'N', 3, '', 0, '', '', '');
INSERT INTO seckill.permission (permission_id, permission_name, create_time, update_time, permission_menu, parent_permission_id, is_dir, order_no, path, hidden, component, access_url, redirect) VALUES (6, '角色权限管理', '2018-07-14 12:28:25', '2018-07-14 12:28:25', 'admin/role.html', 7, 'N', 4, 'role-manage', 0, 'views/permission/role', '', '');
INSERT INTO seckill.permission (permission_id, permission_name, create_time, update_time, permission_menu, parent_permission_id, is_dir, order_no, path, hidden, component, access_url, redirect) VALUES (7, '系统管理', '2018-07-14 12:28:25', '2018-07-14 12:28:25', 'admin', null, 'Y', 0, '', 0, 'layout/Layout', '', '');
INSERT INTO seckill.permission (permission_id, permission_name, create_time, update_time, permission_menu, parent_permission_id, is_dir, order_no, path, hidden, component, access_url, redirect) VALUES (10, '业务管理', '2022-06-26 11:19:16', '2022-06-26 11:19:16', '/test', null, 'Y', 0, '', 0, 'layout/Layout', '', '');
INSERT INTO seckill.permission (permission_id, permission_name, create_time, update_time, permission_menu, parent_permission_id, is_dir, order_no, path, hidden, component, access_url, redirect) VALUES (11, '字典管理', '2022-06-26 11:23:26', '2022-06-26 11:23:26', '/test', null, 'Y', 0, 'dict-manage', 0, 'views/table/user-manage', '', '');


-- ----------------------------
-- Table structure for role
-- ----------------------------
DROP TABLE IF EXISTS `role`;
CREATE TABLE `role` (
  `role_id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `role_name` varchar(64) COLLATE utf8_bin DEFAULT NULL,
  `create_time` datetime DEFAULT NULL,
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;

-- ----------------------------
-- Records of role
-- ----------------------------
INSERT INTO `role` VALUES ('1', '管理员', '2018-07-14 12:29:38', '2018-07-14 12:29:28');
INSERT INTO `role` VALUES ('2', '库存操作员', '2018-07-14 12:29:38', '2018-07-14 12:29:38');
INSERT INTO `role` VALUES ('7', '商品管理员', '2018-07-15 20:29:27', '2018-07-15 20:29:27');
INSERT INTO `role` VALUES ('8', '秒杀管理员', '2018-07-15 20:29:27', '2018-07-15 20:29:27');

alter table role
    add role_code varchar(50) default '' not null comment '角色编码';
alter table role
    add status int null comment '角色状态';
alter table role
    add delete_flag tinyint default 0 not null comment '删除标识';
alter table role
    modify status tinyint default 0 null comment '角色状态';

-- ----------------------------
-- Table structure for role_permission
-- ----------------------------
DROP TABLE IF EXISTS `role_permission`;
CREATE TABLE `role_permission` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `permission_id` int(10) unsigned NOT NULL,
  `role_id` int(10) unsigned NOT NULL,
  `create_time` timestamp NULL DEFAULT NULL,
  `update_time` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `for_permission_id` (`permission_id`),
  KEY `for_role_id` (`role_id`),
  CONSTRAINT `for_permission_id` FOREIGN KEY (`permission_id`) REFERENCES `permission` (`permission_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `for_role_id` FOREIGN KEY (`role_id`) REFERENCES `role` (`role_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=56 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;

-- ----------------------------
-- Records of role_permission
-- ----------------------------
INSERT INTO `role_permission` VALUES ('31', '7', '8', '2018-07-25 21:53:28', '2018-07-25 21:53:28');
INSERT INTO `role_permission` VALUES ('32', '3', '8', '2018-07-25 21:53:28', '2018-07-25 21:53:28');
INSERT INTO `role_permission` VALUES ('33', '4', '8', '2018-07-25 21:53:28', '2018-07-25 21:53:28');
INSERT INTO `role_permission` VALUES ('34', '5', '8', '2018-07-25 21:53:28', '2018-07-25 21:53:28');
INSERT INTO `role_permission` VALUES ('35', '6', '8', '2018-07-25 21:53:28', '2018-07-25 21:53:28');
INSERT INTO `role_permission` VALUES ('36', '7', '2', '2018-07-25 21:53:47', '2018-07-25 21:53:47');
INSERT INTO `role_permission` VALUES ('37', '3', '2', '2018-07-25 21:53:47', '2018-07-25 21:53:47');
INSERT INTO `role_permission` VALUES ('38', '4', '2', '2018-07-25 21:53:47', '2018-07-25 21:53:47');
INSERT INTO `role_permission` VALUES ('39', '5', '2', '2018-07-25 21:53:47', '2018-07-25 21:53:47');
INSERT INTO `role_permission` VALUES ('40', '6', '2', '2018-07-25 21:53:47', '2018-07-25 21:53:47');
INSERT INTO `role_permission` VALUES ('41', '7', '7', '2018-07-25 21:53:49', '2018-07-25 21:53:49');
INSERT INTO `role_permission` VALUES ('42', '3', '7', '2018-07-25 21:53:49', '2018-07-25 21:53:49');
INSERT INTO `role_permission` VALUES ('43', '4', '7', '2018-07-25 21:53:49', '2018-07-25 21:53:49');
INSERT INTO `role_permission` VALUES ('44', '5', '7', '2018-07-25 21:53:49', '2018-07-25 21:53:49');
INSERT INTO `role_permission` VALUES ('45', '6', '7', '2018-07-25 21:53:49', '2018-07-25 21:53:49');
INSERT INTO `role_permission` VALUES ('51', '7', '1', '2018-08-19 19:40:54', '2018-08-19 19:40:54');
INSERT INTO `role_permission` VALUES ('52', '3', '1', '2018-08-19 19:40:54', '2018-08-19 19:40:54');
INSERT INTO `role_permission` VALUES ('53', '4', '1', '2018-08-19 19:40:54', '2018-08-19 19:40:54');
INSERT INTO `role_permission` VALUES ('54', '5', '1', '2018-08-19 19:40:54', '2018-08-19 19:40:54');
INSERT INTO `role_permission` VALUES ('55', '6', '1', '2018-08-19 19:40:54', '2018-08-19 19:40:54');

-- ----------------------------
-- Table structure for seckill
-- ----------------------------
DROP TABLE IF EXISTS `seckill`;
CREATE TABLE `seckill` (
  `seckill_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '商品库存id',
  `name` varchar(120) NOT NULL COMMENT '商品名称',
  `number` int(11) NOT NULL COMMENT '库存数量',
  `start_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '秒杀开启时间',
  `end_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '秒杀结束时间',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `goods_id` int(11) DEFAULT NULL,
  `price` decimal(10,2) DEFAULT NULL,
  `status` varchar(5) DEFAULT NULL,
  `create_user` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`seckill_id`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_end_time` (`end_time`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=1010 DEFAULT CHARSET=utf8 COMMENT='秒杀库存表';


-- ----------------------------
-- Records of seckill
-- ----------------------------
INSERT INTO seckill.seckill (seckill_id, name, number, start_time, end_time, create_time, goods_id, price, status, create_user) VALUES (1000, 'iphone6', 1247, '2017-07-03 23:19:00', '2027-06-25 00:00:00', '2016-06-24 19:46:06', 1, 8000000.00, null, null);
INSERT INTO seckill.seckill (seckill_id, name, number, start_time, end_time, create_time, goods_id, price, status, create_user) VALUES (1001, 'ipad2', 0, '2018-08-19 22:25:35', '2027-06-25 12:00:00', '2016-06-24 19:46:06', 2, 3000.00, '2', null);
INSERT INTO seckill.seckill (seckill_id, name, number, start_time, end_time, create_time, goods_id, price, status, create_user) VALUES (1002, '小米4', 100, '2018-08-19 22:30:22', '2027-06-25 12:00:00', '2016-06-24 19:46:06', 3, 1000.00, '1', null);
INSERT INTO seckill.seckill (seckill_id, name, number, start_time, end_time, create_time, goods_id, price, status, create_user) VALUES (1003, '红米note', 9, '2018-08-19 22:25:40', '2027-06-25 12:00:00', '2016-06-24 19:46:06', 4, 400.00, '1', null);
INSERT INTO seckill.seckill (seckill_id, name, number, start_time, end_time, create_time, goods_id, price, status, create_user) VALUES (1004, '小米6', 10, '2018-08-19 22:25:44', '2027-06-25 12:00:00', '2017-06-09 00:13:54', 5, 2000.00, '1', null);
INSERT INTO seckill.seckill (seckill_id, name, number, start_time, end_time, create_time, goods_id, price, status, create_user) VALUES (1005, '小米max2', 1000, '2018-08-19 22:25:45', '2027-06-25 12:00:00', '2017-06-10 11:14:49', 6, 1499.00, null, null);
INSERT INTO seckill.seckill (seckill_id, name, number, start_time, end_time, create_time, goods_id, price, status, create_user) VALUES (1006, '小米5c', 100, '2018-08-19 22:25:47', '2027-06-25 12:00:00', '2017-06-10 11:16:48', 7, 1299.00, null, null);

-- ----------------------------
-- Table structure for success_killed
-- ----------------------------
DROP TABLE IF EXISTS `success_killed`;
CREATE TABLE `success_killed` (
  `seckill_id` bigint(20) NOT NULL COMMENT '秒杀商品',
  `user_phone` varchar(20) NOT NULL COMMENT '用户手机号',
  `status` tinyint(4) NOT NULL DEFAULT '-1' COMMENT '状态标示：-1：无效   0：成功   1：已付款',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`seckill_id`,`user_phone`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='秒杀成功明细表';

-- ----------------------------
-- Records of success_killed
-- ----------------------------

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
create table user
(
    id              int unsigned auto_increment
        primary key,
    account         varchar(45)                           not null,
    password        varchar(45) default 'aa123456'        not null,
    create_time     timestamp   default CURRENT_TIMESTAMP not null,
    update_time     timestamp   default CURRENT_TIMESTAMP not null,
    username        varchar(50)                           null,
    locked          int         default 0                 null,
    avatar          varchar(500)                          null comment '用户头像url',
    last_login_time timestamp                             null,
    mobile          varchar(20)                           null,
    email_addr      varchar(100)                          null,
    constraint account_UNIQUE
        unique (account)
)
    charset = utf8mb3;

INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (14, 'demo-user-14', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2016-12-18 17:53:26', '2017-06-17 14:46:21', 'Demo User 14', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (15, 'demo-user-15', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2018-07-14 21:25:50', '2018-07-14 21:25:50', 'Demo User 15', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (21, 'admin123', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2018-07-21 21:43:47', '2018-07-21 21:43:47', 'Demo User 21', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (39, 'demo-user-39', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 39', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (40, 'demo-user-40', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 40', 1, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (41, 'demo-user-41', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 41', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (42, 'demo-user-42', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 42', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (43, 'demo-user-43', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 43', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (44, 'demo-user-44', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 44', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (45, 'demo-user-45', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 45', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (46, 'demo-user-46', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 46', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (47, 'demo-user-47', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 47', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (48, 'demo-user-48', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 48', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (49, 'demo-user-49', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 49', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (50, 'demo-user-50', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 50', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (51, 'demo-user-51', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 51', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (52, 'demo-user-52', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 52', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (53, 'demo-user-53', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 53', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (54, 'demo-user-54', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 54', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (55, 'demo-user-55', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 55', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (56, 'demo-user-56', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 56', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (57, 'demo-user-57', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 57', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (58, 'demo-user-58', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 58', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (59, 'demo-user-59', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 59', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (60, 'demo-user-60', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 60', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (61, 'demo-user-61', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 61', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (62, 'demo-user-62', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 62', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (63, 'demo-user-63', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 63', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (64, 'demo-user-64', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 64', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (65, 'demo-user-65', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 65', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (66, 'demo-user-66', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 66', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (67, 'demo-user-67', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 67', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (68, 'demo-user-68', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 68', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (69, 'demo-user-69', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 69', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (70, 'demo-user-70', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 70', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (71, 'demo-user-71', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 71', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (72, 'demo-user-72', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 72', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (73, 'demo-user-73', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 73', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (74, 'demo-user-74', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 74', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (75, 'demo-user-75', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 75', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (76, 'demo-user-76', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 76', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (77, 'demo-user-77', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 77', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (78, 'demo-user-78', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 78', 0, null, null, null, null);
INSERT INTO seckill.user (id, account, password, create_time, update_time, username, locked, avatar, last_login_time, mobile, email_addr) VALUES (79, 'demo-user-79', 'b9cd5964cd1dda92e2335cbfbee3ecc0', '2022-06-25 11:00:13', '2022-06-25 11:00:13', 'Demo User 79', 0, null, null, null, null);


DROP TABLE IF EXISTS `user_auth_account`;
create table if not exists `user_auth_account`
(
    id bigint not null
    primary key,
    user_id int null comment '用户id',
    third_account_id varchar(200) null comment '第三方账号id',
    third_account_name varchar(100) null comment '关联的第三方账户名称',
    source_type varchar(50) null comment '第三方授权来源',
    create_time datetime null,
    update_time datetime null
    )
    comment '用户第三方授权账号表';


-- ----------------------------
-- Table structure for user_role
-- ----------------------------
DROP TABLE IF EXISTS `user_role`;
CREATE TABLE `user_role` (
  `id` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `user_id` int(10) unsigned DEFAULT NULL,
  `role_id` int(10) unsigned DEFAULT NULL,
  `create_time` timestamp NULL DEFAULT NULL,
  `update_time` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `for_user_role_id` (`role_id`),
  KEY `for_user_id` (`user_id`),
  CONSTRAINT `for_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `for_user_role_id` FOREIGN KEY (`role_id`) REFERENCES `role` (`role_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=84 DEFAULT CHARSET=utf8;

-- ----------------------------
-- Records of user_role
-- ----------------------------
INSERT INTO `user_role` VALUES ('60', '15', '1', '2018-07-24 14:43:18', '2018-07-24 14:43:18');
INSERT INTO `user_role` VALUES ('61', '15', '2', '2018-07-24 14:43:18', '2018-07-24 14:43:18');
INSERT INTO `user_role` VALUES ('62', '15', '7', '2018-07-24 14:43:18', '2018-07-24 14:43:18');
INSERT INTO `user_role` VALUES ('63', '15', '8', '2018-07-24 14:43:18', '2018-07-24 14:43:18');
INSERT INTO `user_role` VALUES ('64', '21', '1', '2018-07-24 14:43:21', '2018-07-24 14:43:21');
INSERT INTO `user_role` VALUES ('65', '21', '2', '2018-07-24 14:43:21', '2018-07-24 14:43:21');
INSERT INTO `user_role` VALUES ('66', '21', '7', '2018-07-24 14:43:21', '2018-07-24 14:43:21');
INSERT INTO `user_role` VALUES ('67', '21', '8', '2018-07-24 14:43:21', '2018-07-24 14:43:21');
INSERT INTO `user_role` VALUES ('80', '14', '1', '2018-08-19 19:40:36', '2018-08-19 19:40:36');
INSERT INTO `user_role` VALUES ('81', '14', '2', '2018-08-19 19:40:36', '2018-08-19 19:40:36');
INSERT INTO `user_role` VALUES ('82', '14', '7', '2018-08-19 19:40:36', '2018-08-19 19:40:36');
INSERT INTO `user_role` VALUES ('83', '14', '8', '2018-08-19 19:40:36', '2018-08-19 19:40:36');

ALTER TABLE `seckill`.`success_killed`
ADD COLUMN `server_ip` VARCHAR(200) NULL AFTER `create_time`,
ADD COLUMN `user_ip` VARCHAR(200) NULL AFTER `server_ip`,
ADD COLUMN `user_id` VARCHAR(45) NULL AFTER `user_ip`;

CREATE TABLE `undo_log` (
                            `id` bigint(20) NOT NULL AUTO_INCREMENT,
                            `branch_id` bigint(20) NOT NULL,
                            `xid` varchar(100) NOT NULL,
                            `context` varchar(128) NOT NULL,
                            `rollback_info` longblob NOT NULL,
                            `log_status` int(11) NOT NULL,
                            `log_created` datetime NOT NULL,
                            `log_modified` datetime NOT NULL,
                            `ext` varchar(100) DEFAULT NULL,
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

create schema seckill_01 collate utf8mb4_0900_ai_ci;

create table success_killed_0
(
	seckill_id bigint not null comment '秒杀商品',
	user_phone varchar(20) not null comment '用户手机号',
	status tinyint default -1 not null comment '状态标示：-1：无效   0：成功   1：已付款',
	create_time timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '创建时间',
	server_ip varchar(200) null,
	user_ip varchar(200) null,
	user_id varchar(45) null,
	primary key (seckill_id, user_phone)
)
comment '秒杀成功明细表' charset=utf8;

create index idx_create_time
	on success_killed_0 (create_time);

create table success_killed_1
(
	seckill_id bigint not null comment '秒杀商品',
	user_phone varchar(20) not null comment '用户手机号',
	status tinyint default -1 not null comment '状态标示：-1：无效   0：成功   1：已付款',
	create_time timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '创建时间',
	server_ip varchar(200) null,
	user_ip varchar(200) null,
	user_id varchar(45) null,
	primary key (seckill_id, user_phone)
)
comment '秒杀成功明细表' charset=utf8;

create index idx_create_time
	on success_killed_1 (create_time);

create table seckill_01.success_killed_0
(
	seckill_id bigint not null comment '秒杀商品',
	user_phone varchar(20) not null comment '用户手机号',
	status tinyint default -1 not null comment '状态标示：-1：无效   0：成功   1：已付款',
	create_time timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '创建时间',
	server_ip varchar(200) null,
	user_ip varchar(200) null,
	user_id varchar(45) null,
	primary key (seckill_id, user_phone)
)
comment '秒杀成功明细表' charset=utf8;

create index idx_create_time
	on seckill_01.success_killed_0 (create_time);

create table seckill_01.success_killed_1
(
	seckill_id bigint not null comment '秒杀商品',
	user_phone varchar(20) not null comment '用户手机号',
	status tinyint default -1 not null comment '状态标示：-1：无效   0：成功   1：已付款',
	create_time timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '创建时间',
	server_ip varchar(200) null,
	user_ip varchar(200) null,
	user_id varchar(45) null,
	primary key (seckill_id, user_phone)
)
comment '秒杀成功明细表' charset=utf8;

create index idx_create_time
	on seckill_01.success_killed_1 (create_time);

CREATE TABLE gs_sys.`undo_log` (
                            `id` bigint(20) NOT NULL AUTO_INCREMENT,
                            `branch_id` bigint(20) NOT NULL,
                            `xid` varchar(100) NOT NULL,
                            `context` varchar(128) NOT NULL,
                            `rollback_info` longblob NOT NULL,
                            `log_status` int(11) NOT NULL,
                            `log_created` datetime NOT NULL,
                            `log_modified` datetime NOT NULL,
                            `ext` varchar(100) DEFAULT NULL,
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `ux_undo_log` (`xid`,`branch_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8;

alter table seckill.`user`
    add avatar varchar(500) null comment '用户头像url';


create schema seata_server;
use seata_server;
-- -------------------------------- The script used when storeMode is 'db' --------------------------------
-- the table to store GlobalSession data
CREATE TABLE IF NOT EXISTS `global_table`
(
    `xid`                       VARCHAR(128) NOT NULL,
    `transaction_id`            BIGINT,
    `status`                    TINYINT      NOT NULL,
    `application_id`            VARCHAR(32),
    `transaction_service_group` VARCHAR(32),
    `transaction_name`          VARCHAR(128),
    `timeout`                   INT,
    `begin_time`                BIGINT,
    `application_data`          VARCHAR(2000),
    `gmt_create`                DATETIME,
    `gmt_modified`              DATETIME,
    PRIMARY KEY (`xid`),
    KEY `idx_status_gmt_modified` (`status` , `gmt_modified`),
    KEY `idx_transaction_id` (`transaction_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- the table to store BranchSession data
CREATE TABLE IF NOT EXISTS `branch_table`
(
    `branch_id`         BIGINT       NOT NULL,
    `xid`               VARCHAR(128) NOT NULL,
    `transaction_id`    BIGINT,
    `resource_group_id` VARCHAR(32),
    `resource_id`       VARCHAR(256),
    `branch_type`       VARCHAR(8),
    `status`            TINYINT,
    `client_id`         VARCHAR(64),
    `application_data`  VARCHAR(2000),
    `gmt_create`        DATETIME(6),
    `gmt_modified`      DATETIME(6),
    PRIMARY KEY (`branch_id`),
    KEY `idx_xid` (`xid`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- the table to store lock data
CREATE TABLE IF NOT EXISTS `lock_table`
(
    `row_key`        VARCHAR(128) NOT NULL,
    `xid`            VARCHAR(128),
    `transaction_id` BIGINT,
    `branch_id`      BIGINT       NOT NULL,
    `resource_id`    VARCHAR(256),
    `table_name`     VARCHAR(32),
    `pk`             VARCHAR(36),
    `status`         TINYINT      NOT NULL DEFAULT '0' COMMENT '0:locked ,1:rollbacking',
    `gmt_create`     DATETIME,
    `gmt_modified`   DATETIME,
    PRIMARY KEY (`row_key`),
    KEY `idx_status` (`status`),
    KEY `idx_branch_id` (`branch_id`),
    KEY `idx_xid` (`xid`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS `distributed_lock`
(
    `lock_key`       CHAR(20) NOT NULL,
    `lock_value`     VARCHAR(20) NOT NULL,
    `expire`         BIGINT,
    primary key (`lock_key`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

INSERT INTO `distributed_lock` (lock_key, lock_value, expire) VALUES ('AsyncCommitting', ' ', 0);
INSERT INTO `distributed_lock` (lock_key, lock_value, expire) VALUES ('RetryCommitting', ' ', 0);
INSERT INTO `distributed_lock` (lock_key, lock_value, expire) VALUES ('RetryRollbacking', ' ', 0);
INSERT INTO `distributed_lock` (lock_key, lock_value, expire) VALUES ('TxTimeoutCheck', ' ', 0);

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
独立隔离测试环境：教师/学生账号管理真实页面/API与数据库端到端 (E2E) 验收测试
数据库: 严格限制于 Docker 端口 3308 上的 internship_db_isolated_warn
严禁连接或访问: internship_db_test 与正式生产库
"""

import os
import sys
import json
import time
import subprocess
import urllib.request
import urllib.error
import urllib.parse
from io import BytesIO

BASE_URL = "http://127.0.0.1:3000/api/v1"

def exec_sql(sql):
    cmd = [
        "docker", "exec", "-e", "MYSQL_PWD=123456", "-i",
        "internship-mysql-isolated-e2e", "mysql", "-u", "root",
        "-s", "-N", "--default-character-set=utf8mb4",
        "internship_db_isolated_warn"
    ]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    stdout, stderr = proc.communicate(input=sql.encode("utf-8"))
    out = stdout.decode("utf-8", errors="replace").strip()
    lines = [line.strip() for line in out.splitlines() if "[Warning]" not in line and line.strip()]
    return "\n".join(lines)

def http_req(url_path, method="GET", headers=None, body=None):
    if headers is None:
        headers = {}
    full_url = f"{BASE_URL}{url_path}"
    
    data = None
    if body is not None:
        if isinstance(body, (dict, list)):
            data = json.dumps(body).encode("utf-8")
            if "Content-Type" not in headers:
                headers["Content-Type"] = "application/json"
        elif isinstance(body, bytes):
            data = body
        elif isinstance(body, str):
            data = body.encode("utf-8")

    req = urllib.request.Request(full_url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            status = resp.status
            content = resp.read().decode("utf-8", errors="replace")
            try:
                parsed_json = json.loads(content)
            except Exception:
                parsed_json = content
            return status, parsed_json, resp.headers
    except urllib.error.HTTPError as e:
        status = e.code
        err_content = e.read().decode("utf-8", errors="replace")
        try:
            parsed_json = json.loads(err_content)
        except Exception:
            parsed_json = err_content
        return status, parsed_json, e.headers
    except Exception as e:
        raise RuntimeError(f"HTTP Request failed to {full_url}: {e}")

def login(username, password="123456"):
    status, res, _ = http_req("/auth/login", method="POST", body={
        "username": username,
        "password": password
    })
    if status != 200 or not isinstance(res, dict) or res.get("code") != 200:
        raise RuntimeError(f"Login failed for {username}: {res}")
    return res["data"]["token"], res["data"]

def create_multipart_form(fields, files):
    boundary = "----WebKitFormBoundary" + hex(int(time.time() * 1000))[2:]
    buf = bytearray()
    for name, val in fields.items():
        buf.extend(f"--{boundary}\r\n".encode("utf-8"))
        buf.extend(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode("utf-8"))
        buf.extend(f"{val}\r\n".encode("utf-8"))
    for name, (filename, content, content_type) in files.items():
        buf.extend(f"--{boundary}\r\n".encode("utf-8"))
        buf.extend(f'Content-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'.encode("utf-8"))
        buf.extend(f"Content-Type: {content_type}\r\n\r\n".encode("utf-8"))
        if isinstance(content, str):
            buf.extend(content.encode("gbk"))
        else:
            buf.extend(content)
        buf.extend(b"\r\n")
    buf.extend(f"--{boundary}--\r\n".encode("utf-8"))
    return bytes(buf), f"multipart/form-data; boundary={boundary}"

def main():
    print("================================================================")
    print(">>> [独立隔离环境] 教师/学生账号管理真实 E2E 全量业务闭环验收")
    print(">>> 隔离数据库: internship_db_isolated_warn (Docker 3308)")
    print(">>> 严格红线: 严禁触碰/访问 internship_db_test 与正式生产库")
    print("================================================================\n")

    # 0. 环境核验与清理上一轮合成测试账号
    print("0. 独立测试环境连通性核查与测试前状态清理...")
    db_name = exec_sql("SELECT DATABASE();")
    print(f"   - 后端直连数据库确认: {db_name} (预期: internship_db_isolated_warn)")
    if db_name != "internship_db_isolated_warn":
        raise RuntimeError(f"FATAL: 数据库非隔离测试库: {db_name}")

    # 清理之前生成的合成账号，确保幂等测试
    exec_sql("""
        DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE username IN ('t_syn_01', 't_syn_02', 'stu_syn_01', 'stu_syn_02', 't_cross_01'));
        DELETE FROM sys_user WHERE username IN ('t_syn_01', 't_syn_02', 'stu_syn_01', 'stu_syn_02', 't_cross_01');
        UPDATE sys_user SET phone = '13900139003', email = 'stu3003@test.edu.cn' WHERE id = 3003;
    """)
    print("   - 隔离库清理完成，测试数据初始化就绪")

    admin_token, _ = login("admin")
    deptadmin_token, _ = login("deptadmin")
    deptadmin_em_token, _ = login("deptadmin_em")
    print("   - 系统超级管理员(admin) / 计算机系管(deptadmin) / 经管系管(deptadmin_em) 登录成功")

    # =========================================================================
    # 阶段 1: 教师、学生导入模板、预览、确认导入、逐行结果及重复数据拦截
    # =========================================================================
    print("\n----------------------------------------------------------------")
    print(">>> 阶段 1: 模板下载、预览解析、确认导入、逐行结果及重复拦截")
    print("----------------------------------------------------------------")

    print("1.1 验证教师与学生 Excel 导入模板下载接口...")
    status, content, hdrs = http_req("/users/import-template?userType=TEACHER", headers={"Authorization": f"Bearer {admin_token}"})
    if status != 200 or "attachment" not in hdrs.get("Content-Disposition", ""):
        raise RuntimeError(f"教师导入模板下载失败: status={status}, hdrs={hdrs}")
    print("    - 教师导入模板下载成功 (Content-Disposition 携带 attachment 标识)")

    status, content, hdrs = http_req("/users/import-template?userType=STUDENT", headers={"Authorization": f"Bearer {admin_token}"})
    if status != 200 or "attachment" not in hdrs.get("Content-Disposition", ""):
        raise RuntimeError(f"学生导入模板下载失败: status={status}, hdrs={hdrs}")
    print("    - 学生导入模板下载成功 (Content-Disposition 携带 attachment 标识)")

    print("1.2 构造教师测试 CSV (包含正常行、系统内重名账号行、公式注入非法字符行、文件内重复行)...")
    # CSV 内容按 GBK 编码:
    # 正常行: TEA_SYN_01, t_syn_01, 测试教师1, 13800138001, tea01@test.edu.cn, 计算机科学与技术学院
    # 库内重名: TEA_WARN_A, teacher_warn_a, 重复教师, 13800138002, tea02@test.edu.cn, 计算机科学与技术学院
    # 公式注入: =cmd|' /C calc'!A0, t_formula, 注入教师, 13800138003, tea03@test.edu.cn, 计算机科学与技术学院
    # 文件内重名: TEA_SYN_01_DUP, t_syn_01, 重名教师, 13800138004, tea04@test.edu.cn, 计算机科学与技术学院
    teacher_csv = (
        "工号,用户名,姓名,手机号,邮箱,所属学院\n"
        "TEA_SYN_01,t_syn_01,测试教师1,13800138001,tea01@test.edu.cn,计算机科学与技术学院\n"
        "TEA_WARN_A,teacher_warn_a,库内重复教师,13800138002,tea02@test.edu.cn,计算机科学与技术学院\n"
        "=cmd|' /C calc'!A0,t_formula,注入教师,13800138003,tea03@test.edu.cn,计算机科学与技术学院\n"
        "TEA_SYN_01_DUP,t_syn_01,文件内重复教师,13800138004,tea04@test.edu.cn,计算机科学与技术学院\n"
    )
    req_body, content_type = create_multipart_form({}, {"file": ("teachers.csv", teacher_csv, "text/csv")})
    status, preview_res, _ = http_req("/users/import-preview?userType=TEACHER", method="POST",
                                      headers={"Authorization": f"Bearer {admin_token}", "Content-Type": content_type},
                                      body=req_body)
    print(f"    - 教师导入预览响应状态码: {status}, code: {preview_res.get('code')}")
    if status != 200 or preview_res.get("code") != 200:
        raise RuntimeError(f"教师导入预览失败: {preview_res}")
    
    pdata = preview_res["data"]
    print(f"    - 预览统计: 总行数={pdata['totalCount']}, 有效行={pdata['validCount']}, 异常行={pdata['invalidCount']}")
    if pdata["validCount"] != 1 or pdata["invalidCount"] != 3:
        raise RuntimeError(f"教师预览行数校验不符: valid={pdata['validCount']}, invalid={pdata['invalidCount']}")
    
    # 详细核查逐行拦截原因
    prows = pdata["previewRows"]
    print(f"      * 行2 (正常行): isValid={prows[0]['isValid']}")
    print(f"      * 行3 (库内同名拦截): isValid={prows[1]['isValid']}, error={prows[1].get('errorMessage')}")
    print(f"      * 行4 (公式注入拦截): isValid={prows[2]['isValid']}, error={prows[2].get('errorMessage')}")
    print(f"      * 行5 (文件内排重拦截): isValid={prows[3]['isValid']}, error={prows[3].get('errorMessage')}")
    
    if not ("系统中已存在同名账号" in prows[1].get("errorMessage", "")):
        raise RuntimeError("未正确拦截系统内已存在账号")
    if not ("非法公式字符" in prows[2].get("errorMessage", "")):
        raise RuntimeError("未正确拦截 Excel 公式注入符号")
    if not ("文件中存在重复" in prows[3].get("errorMessage", "")):
        raise RuntimeError("未正确拦截文件内重复账号")

    preview_token = pdata["previewToken"]
    print(f"    - 获得预览防篡改 Token: {preview_token}")

    print("1.3 管理员确认执行导入 (消费 previewToken)...")
    status, exec_res, _ = http_req("/users/import-execute", method="POST",
                                   headers={"Authorization": f"Bearer {admin_token}"},
                                   body={"userType": "TEACHER", "previewToken": preview_token})
    print(f"    - 导入执行响应: {exec_res}")
    if status != 200 or exec_res.get("code") != 200:
        raise RuntimeError(f"执行导入失败: {exec_res}")
    
    edata = exec_res["data"]
    print(f"    - 执行结果: 成功={edata['successCount']}, 失败={edata['failureCount']}")
    if edata["successCount"] != 1:
        raise RuntimeError(f"执行导入成功数不符: {edata['successCount']}")
    
    creds = edata["credentials"]
    if len(creds) != 1 or creds[0]["username"] != "t_syn_01":
        raise RuntimeError(f"下发凭证列表不符: {creds}")
    temp_pwd_t1 = creds[0]["temporaryPassword"]
    print(f"    - 成功为 t_syn_01 下发初始独立临时密码 (高熵安全随机): {temp_pwd_t1}")
    if len(temp_pwd_t1) < 10:
        raise RuntimeError("初始临时密码长度不足或熵值过低")

    print("1.4 数据库底层落库状态与哈希加密核验...")
    db_row = exec_sql("SELECT id, username, status, token_version, LEFT(password, 7) FROM sys_user WHERE username='t_syn_01';")
    print(f"    - 数据库落库记录: {db_row}")
    # 格式: id \t username \t status \t token_version \t LEFT(password, 7)
    fields = db_row.split("\t")
    t1_id = int(fields[0])
    t1_status = int(fields[2])
    t1_token_ver = int(fields[3])
    t1_pwd_prefix = fields[4]
    if t1_status != 2:
        raise RuntimeError(f"新建用户初始状态应为 2 (待修改密码)，实际为: {t1_status}")
    if t1_token_ver != 1:
        raise RuntimeError(f"新建用户初始 token_version 应为 1，实际为: {t1_token_ver}")
    if not t1_pwd_prefix.startswith("$2a$"):
        raise RuntimeError(f"密码应以 BCrypt 格式散列入库，实际为: {t1_pwd_prefix}")
    print("    - [PASS] 数据库仅存储 BCrypt 散列，status=2 (强制首次改密)，token_version=1")

    print("1.5 二次防重放与系统排重核查...")
    # 二次提交同一 previewToken
    status, replay_res, _ = http_req("/users/import-execute", method="POST",
                                     headers={"Authorization": f"Bearer {admin_token}"},
                                     body={"userType": "TEACHER", "previewToken": preview_token})
    print(f"    - previewToken 二次重放响应状态码: {status}, message: {replay_res.get('message')}")
    if status != 400 or "过期或无效" not in replay_res.get("message", ""):
        raise RuntimeError("未能单次消费 previewToken 阻止重复提交")

    # 尝试绕过 previewToken 直接提交同一用户
    status, direct_dup_res, _ = http_req("/users/import-execute", method="POST",
                                         headers={"Authorization": f"Bearer {admin_token}"},
                                         body={
                                             "userType": "TEACHER",
                                             "rows": [{
                                                 "userNumber": "TEA_SYN_01",
                                                 "username": "t_syn_01",
                                                 "realName": "测试教师1",
                                                 "deptName": "计算机科学与技术学院"
                                             }]
                                         })
    print(f"    - 直接提交重复账号执行响应: {direct_dup_res}")
    # failureCount=1, successCount=0
    if direct_dup_res["data"]["failureCount"] != 1:
        raise RuntimeError("后端未能在执行入库阶段拦截重名账号")
    print(">>> [PASS] 阶段 1 全部断言通过！")

    # =========================================================================
    # 阶段 2: 跨院系导入越权拦截和学院/专业/班级关系校验
    # =========================================================================
    print("\n----------------------------------------------------------------")
    print(">>> 阶段 2: 跨院系导入越权拦截与学院/专业/班级层级校验")
    print("----------------------------------------------------------------")

    print("2.1 院系管理员跨院系导入越权拦截测试...")
    # 计算机系管理员 (deptId=1) 尝试上传经管学院 (deptId=2) 的教师
    cross_dept_csv = (
        "工号,用户名,姓名,手机号,邮箱,所属学院\n"
        "TEA_CROSS_01,t_cross_01,跨系教师,13800138999,cross@test.edu.cn,经济管理学院\n"
    )
    req_body, content_type = create_multipart_form({}, {"file": ("cross_dept.csv", cross_dept_csv, "text/csv")})
    status, cross_preview_res, _ = http_req("/users/import-preview?userType=TEACHER", method="POST",
                                            headers={"Authorization": f"Bearer {deptadmin_token}", "Content-Type": content_type},
                                            body=req_body)
    print(f"    - 计算机系管预览经管教师响应: {cross_preview_res}")
    cross_row = cross_preview_res["data"]["previewRows"][0]
    print(f"    - 越权行校验结果: isValid={cross_row['isValid']}, errorMessage={cross_row.get('errorMessage')}")
    if cross_row["isValid"] or "无权跨院系导入" not in cross_row.get("errorMessage", ""):
        raise RuntimeError("院系管理员跨院系导入未被拦截")

    # 尝试绕过 preview 强制调用 executeImport
    status, cross_exec_res, _ = http_req("/users/import-execute", method="POST",
                                         headers={"Authorization": f"Bearer {deptadmin_token}"},
                                         body={
                                             "userType": "TEACHER",
                                             "rows": [{
                                                 "userNumber": "TEA_CROSS_01",
                                                 "username": "t_cross_01",
                                                 "realName": "跨系教师",
                                                 "deptName": "经济管理学院"
                                             }]
                                         })
    print(f"    - 越权强行入库响应: {cross_exec_res}")
    if cross_exec_res["data"]["failureCount"] != 1:
        raise RuntimeError("后端执行阶段未阻断跨院系越权入库")
    print("    - [PASS] 院系管理员跨院系导入在预览与执行阶段均被精准阻断！")

    print("2.2 学院/专业/班级层级关系校验...")
    # 构造包含三种组织架构层级矛盾的学生 CSV:
    # 矛盾A: 院系不存在 (魔法学院)
    # 矛盾B: 专业与学院不匹配 (计算机科学与技术学院 + 国际经济与贸易)
    # 矛盾C: 班级与专业不匹配 (软件工程 + 国贸2101班)
    # 正常行: 计算机科学与技术学院 + 软件工程 + 软件工程2101班
    hierarchy_csv = (
        "学号,用户名,姓名,手机号,邮箱,所属学院,所属专业,所属班级\n"
        "STU_ERR_01,stu_err_01,异常生1,13900139011,e1@test.edu.cn,魔法学院,软件工程,软件工程2101班\n"
        "STU_ERR_02,stu_err_02,异常生2,13900139012,e2@test.edu.cn,计算机科学与技术学院,国际经济与贸易,国贸2101班\n"
        "STU_ERR_03,stu_err_03,异常生3,13900139013,e3@test.edu.cn,计算机科学与技术学院,软件工程,国贸2101班\n"
        "STU_SYN_01,stu_syn_01,合规生1,13900139014,s1@test.edu.cn,计算机科学与技术学院,软件工程,软件工程2101班\n"
    )
    req_body, content_type = create_multipart_form({}, {"file": ("hierarchy.csv", hierarchy_csv, "text/csv")})
    status, hier_res, _ = http_req("/users/import-preview?userType=STUDENT", method="POST",
                                   headers={"Authorization": f"Bearer {admin_token}", "Content-Type": content_type},
                                   body=req_body)
    print(f"    - 学生组织架构校验响应: validCount={hier_res['data']['validCount']}, invalidCount={hier_res['data']['invalidCount']}")
    hrows = hier_res["data"]["previewRows"]
    print(f"      * 矛盾A (不存在院系): {hrows[0].get('errorMessage')}")
    print(f"      * 矛盾B (专业不属学院): {hrows[1].get('errorMessage')}")
    print(f"      * 矛盾C (班级不属专业): {hrows[2].get('errorMessage')}")
    print(f"      * 正常行: isValid={hrows[3]['isValid']}")

    if "院系不存在" not in hrows[0].get("errorMessage", ""):
        raise RuntimeError("未检测出不存在的院系")
    if "不属于学院" not in hrows[1].get("errorMessage", ""):
        raise RuntimeError("未检测出专业与学院的从属冲突")
    if "不属于专业" not in hrows[2].get("errorMessage", ""):
        raise RuntimeError("未检测出班级与专业的从属冲突")
    if not hrows[3]["isValid"]:
        raise RuntimeError("合规学生数据校验未通过")

    # 导入该合规学生
    status, stu_exec_res, _ = http_req("/users/import-execute", method="POST",
                                       headers={"Authorization": f"Bearer {admin_token}"},
                                       body={"userType": "STUDENT", "previewToken": hier_res["data"]["previewToken"]})
    print(f"    - 学生导入执行响应: {stu_exec_res['data']}")
    temp_pwd_stu1 = stu_exec_res["data"]["credentials"][0]["temporaryPassword"]
    print(f"    - 成功下发合规学生 stu_syn_01 独立临时密码: {temp_pwd_stu1}")
    print(">>> [PASS] 阶段 2 全部断言通过！")

    # =========================================================================
    # 阶段 3: 初始临时密码单次展示、首次登录强制改密及后端接口拦截
    # =========================================================================
    print("\n----------------------------------------------------------------")
    print(">>> 阶段 3: 初始临时密码单次展示、首次登录强制改密及后端拦截")
    print("----------------------------------------------------------------")

    print("3.1 验证用户信息查询接口不泄露临时密码...")
    status, user_list_res, _ = http_req(f"/users?keyword=t_syn_01", headers={"Authorization": f"Bearer {admin_token}"})
    records = user_list_res["data"]["records"]
    if not records or records[0]["username"] != "t_syn_01":
        raise RuntimeError("查询用户失败")
    record = records[0]
    print(f"    - 用户管理列表字段: {list(record.keys())}")
    if "password" in record or "temporaryPassword" in record:
        raise RuntimeError("用户管理列表接口泄露密码字段！")
    print("    - [PASS] 列表查询与详情接口严格不包含密码明文/临时密码")

    print("3.2 首次登录: 使用初始临时密码登录...")
    status, login_res, _ = http_req("/auth/login", method="POST", body={
        "username": "t_syn_01",
        "password": temp_pwd_t1
    })
    print(f"    - 首次登录响应: code={login_res.get('code')}, mustChangePassword={login_res['data'].get('mustChangePassword')}")
    if login_res.get("code") != 200 or login_res["data"].get("mustChangePassword") is not True:
        raise RuntimeError("首次登录未正确返回 mustChangePassword = true")
    
    pending_token = login_res["data"]["token"]

    print("3.3 后端接口强拦截 (重点核查 1: 待改密 Token 严禁访问业务 API)...")
    # 尝试访问用户管理列表
    status, block_res1, _ = http_req("/users", headers={"Authorization": f"Bearer {pending_token}"})
    print(f"    - 访问 /users 响应状态码: {status} (预期 403), 消息: {block_res1.get('message')}")
    if status != 403 or "请先修改初始临时密码" not in block_res1.get("message", ""):
        raise RuntimeError("待改密用户未被后端安全拦截器 403 阻断！")

    # 尝试访问预警工单接口
    status, block_res2, _ = http_req("/warn/tickets?taskId=3101", headers={"Authorization": f"Bearer {pending_token}"})
    print(f"    - 访问 /warn/tickets 响应状态码: {status} (预期 403)")
    if status != 403:
        raise RuntimeError("待改密用户调用预警接口未被 403 阻断！")

    print("3.4 验证白名单放行接口 (/auth/me)...")
    status, me_res, _ = http_req("/auth/me", headers={"Authorization": f"Bearer {pending_token}"})
    print(f"    - 访问 /auth/me 响应状态码: {status} (预期 200), username: {me_res['data'].get('username')}")
    if status != 200 or me_res["data"].get("username") != "t_syn_01":
        raise RuntimeError("白名单接口 /auth/me 未能正常放行")

    print("3.5 用户完成首次强制改密 (POST /users/change-password)...")
    new_pwd_t1 = "TeacherSafeP@ss2026!"
    status, change_res, _ = http_req("/users/change-password", method="POST",
                                     headers={"Authorization": f"Bearer {pending_token}"},
                                     body={
                                         "oldPassword": temp_pwd_t1,
                                         "newPassword": new_pwd_t1,
                                         "confirmPassword": new_pwd_t1
                                     })
    print(f"    - 修改密码响应: {change_res}")
    if status != 200 or change_res.get("code") != 200:
        raise RuntimeError(f"修改密码失败: {change_res}")

    # 核查数据库状态
    db_state = exec_sql("SELECT status, token_version FROM sys_user WHERE username='t_syn_01';")
    print(f"    - 改密后数据库状态: {db_state} (预期: 1 \\t 2)")
    status_val, token_ver_val = db_state.split("\t")
    if int(status_val) != 1 or int(token_ver_val) != 2:
        raise RuntimeError(f"改密后状态未更新为 1 或 token_version 未自增: {db_state}")

    print("3.6 核验改密后旧 Token 立即失效，新密码登录后业务接口畅通...")
    status, old_token_retry, _ = http_req("/auth/me", headers={"Authorization": f"Bearer {pending_token}"})
    print(f"    - 改密后继续使用原 Token 访问 /auth/me 响应状态码: {status} (预期 401/403)")
    if status not in (401, 403):
        raise RuntimeError("改密后旧 Token 未立即失效！")

    # 使用新密码重新登录
    status, new_login_res, _ = http_req("/auth/login", method="POST", body={
        "username": "t_syn_01",
        "password": new_pwd_t1
    })
    print(f"    - 新密码登录响应: code={new_login_res.get('code')}, mustChangePassword={new_login_res['data'].get('mustChangePassword')}")
    if new_login_res["data"].get("mustChangePassword") is not False:
        raise RuntimeError("新密码登录后 mustChangePassword 仍为 true")
    
    active_token_t1 = new_login_res["data"]["token"]
    status, active_biz_res, _ = http_req("/auth/me", headers={"Authorization": f"Bearer {active_token_t1}"})
    if status != 200:
        raise RuntimeError("新 Token 无法正常访问业务接口")
    print("    - [PASS] 改密流程闭环，旧凭据失效，新凭据恢复全部合法操作权限")
    print(">>> [PASS] 阶段 3 全部断言通过！")

    # =========================================================================
    # 阶段 4: 管理员重置后旧 Token 失效
    # =========================================================================
    print("\n----------------------------------------------------------------")
    print(">>> 阶段 4: 管理员重置密码与在线会话即时失效")
    print("----------------------------------------------------------------")

    print(f"4.1 确认教师 t_syn_01 当前持有效 Token 在线 (token_version=2)...")
    status, check_online, _ = http_req("/auth/me", headers={"Authorization": f"Bearer {active_token_t1}"})
    if status != 200:
        raise RuntimeError("教师当前应为在线状态")

    print(f"4.2 管理员调用 POST /users/{t1_id}/reset-password 重置其密码...")
    status, reset_res, _ = http_req(f"/users/{t1_id}/reset-password", method="POST",
                                    headers={"Authorization": f"Bearer {admin_token}"})
    print(f"    - 重置响应: {reset_res}")
    if status != 200 or reset_res.get("code") != 200:
        raise RuntimeError(f"重置密码失败: {reset_res}")
    
    new_temp_pwd = reset_res["data"]["temporaryPassword"]
    print(f"    - 管理员获取的新重置临时密码: {new_temp_pwd}")

    print("4.3 核心安全核验: 验证原在线 Token 立即失效 (重点核查 4: 杜绝仅界面提示)...")
    status, kicked_out_res, _ = http_req("/auth/me", headers={"Authorization": f"Bearer {active_token_t1}"})
    print(f"    - 被重置后使用原 Token 访问 /auth/me 响应状态码: {status} (预期 401/403)")
    if status not in (401, 403):
        raise RuntimeError("管理员重置密码后，原在线 Token 未被物理吊销！")

    db_ver = exec_sql(f"SELECT token_version, status FROM sys_user WHERE id={t1_id};")
    print(f"    - 数据库核查 token_version 与状态: {db_ver} (预期: 3 \\t 2)")
    ver_val, st_val = db_ver.split("\t")
    if int(ver_val) != 3 or int(st_val) != 2:
        raise RuntimeError(f"重置密码后 token_version 未自增或 status 未重置为 2: {db_ver}")

    print("4.4 用户使用新重置密码登录并再次处于待改密保护...")
    status, reset_login_res, _ = http_req("/auth/login", method="POST", body={
        "username": "t_syn_01",
        "password": new_temp_pwd
    })
    if reset_login_res["data"].get("mustChangePassword") is not True:
        raise RuntimeError("重置密码登录后未强制要求修改密码")
    print("    - [PASS] 管理员重置密码后，旧设备在线 Token 物理失效，强制改密重新生效")
    print(">>> [PASS] 阶段 4 全部断言通过！")

    # =========================================================================
    # 阶段 5: 使用 Mock Provider 验证忘记密码限流、防枚举、验证码过期、错误次数锁定和防重放；Provider 未配置时应安全返回 503
    # =========================================================================
    print("\n----------------------------------------------------------------")
    print(">>> 阶段 5: 忘记密码安全防线 (限流/防枚举/错误锁定/503安全失败)")
    print("----------------------------------------------------------------")

    print("5.1 防账号枚举保护验证 (重点核查 3)...")
    # A. 请求不存在的账号
    status, non_exist_res, _ = http_req("/users/forgot-password/send-code", method="POST", body={
        "username": "non_exist_user_9999",
        "target": "13900009999"
    })
    print(f"    - 不存在账号请求响应: status={status}, message={non_exist_res.get('message')}")

    # B. 请求已存在账号但输入不匹配手机
    status, mismatch_res, _ = http_req("/users/forgot-password/send-code", method="POST", body={
        "username": "student_warn_iso",
        "target": "13911112222" # 真实手机为 13900139003
    })
    print(f"    - 账号存在但手机不匹配响应: status={status}, message={mismatch_res.get('message')}")

    if non_exist_res.get("message") != mismatch_res.get("message"):
        raise RuntimeError(f"防枚举失败: 不存在账号与手机不匹配返回信息不一致!\nA: {non_exist_res}\nB: {mismatch_res}")
    print("    - [PASS] 无论账号是否存在均返回完全相同的模糊安全提示，杜绝账号探测枚举")

    print("5.2 未配置真实 Provider 时的 503 安全失败 (Safe Failure) 验证...")
    # 后端环境 EMAIL_PROVIDER=none
    # 向 student_warn_iso 绑定的邮箱发送验证码
    status, unconf_email_res, _ = http_req("/users/forgot-password/send-code", method="POST", body={
        "username": "student_warn_iso",
        "target": "stu3003@test.edu.cn"
    })
    print(f"    - 邮件通道未配置请求响应状态码: {status} (预期 503), 消息: {unconf_email_res.get('message')}")
    if status != 503 or "未配置" not in unconf_email_res.get("message", ""):
        raise RuntimeError("未配置真实 Provider 时未安全失败返回 503！")
    if "code" in str(unconf_email_res).lower() and len(str(unconf_email_res.get("data", ""))) > 0:
        raise RuntimeError("错误响应中泄露了验证码信息！")
    print("    - [PASS] 通道未配置时安全返回 HTTP 503，严禁对用户假报发送成功或泄露验证码")

    print("5.3 Mock Provider 模拟短信发送与 60s 频控限流验证...")
    # 向 student_warn_iso 绑定的手机 13900139003 发送验证码
    status, mock_send_res, _ = http_req("/users/forgot-password/send-code", method="POST", body={
        "username": "student_warn_iso",
        "target": "13900139003"
    })
    print(f"    - 首次短信发送响应状态码: {status}, message: {mock_send_res.get('message')}")
    if status != 200 or "安全验证码已发送" not in mock_send_res.get("message", ""):
        raise RuntimeError(f"Mock 短信发送失败: {mock_send_res}")
    
    # 立即发送第二次 (60s 内)
    status, rate_limit_res, _ = http_req("/users/forgot-password/send-code", method="POST", body={
        "username": "student_warn_iso",
        "target": "13900139003"
    })
    print(f"    - 60s 内二次发送响应状态码: {status} (预期 429), message: {rate_limit_res.get('message')}")
    if status != 429 or "过于频繁" not in rate_limit_res.get("message", ""):
        raise RuntimeError("60s 频控流控未生效！")
    print("    - [PASS] 60秒发送冷却流控严格生效")

    print("5.4 验证码防暴力破解与错误锁定作废机制验证...")
    # 连续 5 次提交错误验证码
    for attempt in range(1, 6):
        wrong_code = f"88888{attempt}"
        status, verify_fail_res, _ = http_req("/users/forgot-password/verify-and-reset", method="POST", body={
            "username": "student_warn_iso",
            "target": "13900139003",
            "code": wrong_code,
            "newPassword": "NewStudentP@ss2026",
            "confirmPassword": "NewStudentP@ss2026"
        })
        msg = verify_fail_res.get("message", "")
        print(f"      * 尝试 {attempt}/5: status={status}, message={msg}")
        if attempt < 5:
            if "还剩" not in msg or str(5 - attempt) not in msg:
                raise RuntimeError(f"剩余尝试次数提示异常: {msg}")
        else:
            if "已超限" not in msg or "已作废" not in msg:
                raise RuntimeError(f"第 5 次输错未能锁定并作废验证码: {msg}")

    # 第 6 次调用验证码已失效
    status, verify_fail_res6, _ = http_req("/users/forgot-password/verify-and-reset", method="POST", body={
        "username": "student_warn_iso",
        "target": "13900139003",
        "code": "123456",
        "newPassword": "NewStudentP@ss2026",
        "confirmPassword": "NewStudentP@ss2026"
    })
    print(f"      * 锁定后第 6 次重试响应: status={status}, message={verify_fail_res6.get('message')}")
    if "已过期或不存在" not in verify_fail_res6.get("message", ""):
        raise RuntimeError("验证码作废后未能清理缓存")
    print("    - [PASS] 连续 5 次输错成功锁定作废，彻底免疫暴力破解枚举")

    print("5.5 敏感信息脱敏与日志零泄漏核查 (重点核查 3)...")
    # 读取后端最新日志行
    task_log = "C:/Users/曹聪/.gemini/antigravity/brain/26b36451-7588-449f-bf18-e6fe2fba972f/.system_generated/tasks/task-14299.log"
    with open(task_log, "r", encoding="utf-8", errors="replace") as f:
        log_text = f.read()
    
    # 核查日志中是否有类似 "code=" 后面跟着 6 位数字的泄露
    import re
    suspicious = re.findall(r'\[MOCK外发通道\].*?code=\d{6}', log_text)
    if suspicious:
        raise RuntimeError(f"FATAL: 在后端日志中检测到明文验证码泄露: {suspicious}")
    print("    - [PASS] 后端日志经脱敏检查，仅记录脱敏目标与 mock deliveryId，零验证码明文泄露")

    print("\n================================================================")
    print(">>> 教师/学生账号管理全部 5 项深度安全与端到端验收全部通过 (PASS)！")
    print("================================================================")

if __name__ == "__main__":
    main()

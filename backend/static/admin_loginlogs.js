// 管理端：登录日志页（admin_loginlogs.js）
(function(){
  if (typeof R === "undefined") { console.warn("R 未定义"); return; }

  var ROLE_TXT = { admin: "管理员", reviewer: "审核员", user: "用户" };

  function roleTag(role){
    var t = ROLE_TXT[role] || role || "-";
    var color = role === "admin" ? "#3b6ef5" : (role === "reviewer" ? "#7c5cff" : "#22b573");
    return "<span style='display:inline-block;padding:2px 9px;border-radius:999px;background:"+color+"1a;color:"+color+";font-size:12px;font-weight:600'>"+t+"</span>";
  }

  window.__loginlogs_state = { role: "", keyword: "", offset: 0, limit: 100 };

  R.loginlogs = async function(){
    var st = window.__loginlogs_state;
    var h = "";
    h += "<div class=card>";
    h += "<div class=sec-title style='margin-top:0'>🕵️ 登录日志</div>";
    h += "<div class=muted style='margin-bottom:12px'>记录管理员、审核员、普通用户的全部登录行为，失败记录会标注原因。数据可作为责任认定依据。</div>";
    h += "<div style='display:flex;gap:8px;flex-wrap:wrap;margin-bottom:14px'>";
    h += "<select id='llRole' onchange='window.__llRoleChange()' style='padding:8px 12px;border:1px solid #e8ecf3;border-radius:9px'>";
    h += "<option value=''>全部角色</option>";
    h += "<option value='admin'>管理员</option>";
    h += "<option value='reviewer'>审核员</option>";
    h += "<option value='user'>普通用户</option>";
    h += "</select>";
    h += "<input id='llKw' placeholder='搜索账号或IP' style='padding:8px 12px;border:1px solid #e8ecf3;border-radius:9px;flex:1;min-width:140px' onkeydown=\"if(event.key==='Enter')window.__llSearch()\">";
    h += "<button class='btn2' style='padding:8px 16px;font-size:13px' onclick='window.__llSearch()'>查询</button>";
    h += "<button class='btn2 g' style='padding:8px 16px;font-size:13px' onclick='window.__llExport()'>导出CSV</button>";
    h += "</div>";
    h += "<div id='llStats' style='margin-bottom:14px'></div>";
    h += "<div id='llList'><div class=empty>加载中...</div></div>";
    h += "</div>";
    $("content").innerHTML = h;

    if (st.role) $("llRole").value = st.role;
    if (st.keyword) $("llKw").value = st.keyword;

    await window.__llLoadStats();
    await window.__llLoad(0);
  };

  window.__llRoleChange = function(){
    window.__loginlogs_state.role = $("llRole").value;
    window.__loginlogs_state.keyword = $("llKw").value.trim();
    window.__llLoad(0);
  };

  window.__llSearch = function(){
    window.__loginlogs_state.role = $("llRole").value;
    window.__loginlogs_state.keyword = $("llKw").value.trim();
    window.__llLoad(0);
  };

  window.__llLoadStats = async function(){
    try{
      var s = await api("/api/admin/login-logs/stats?days=7");
      var h = "<div style='display:flex;gap:10px;flex-wrap:wrap'>";
      h += "<div style='flex:1;min-width:110px;background:#eef3ff;border-radius:12px;padding:12px 14px'>";
      h += "<div style='font-size:22px;font-weight:800;color:#3b6ef5'>"+(s.total||0)+"</div>";
      h += "<div style='font-size:12.5px;color:#8a94a6;margin-top:2px'>近7天登录</div></div>";
      h += "<div style='flex:1;min-width:110px;background:#fff1f1;border-radius:12px;padding:12px 14px'>";
      h += "<div style='font-size:22px;font-weight:800;color:#ff5b5b'>"+(s.fail||0)+"</div>";
      h += "<div style='font-size:12.5px;color:#8a94a6;margin-top:2px'>登录失败</div></div>";
      var br = s.by_role || {};
      for (var k in br){
        h += "<div style='flex:1;min-width:110px;background:#f5f7fb;border-radius:12px;padding:12px 14px'>";
        h += "<div style='font-size:22px;font-weight:800'>"+br[k]+"</div>";
        h += "<div style='font-size:12.5px;color:#8a94a6;margin-top:2px'>"+(ROLE_TXT[k]||k)+"</div></div>";
      }
      h += "</div>";
      $("llStats").innerHTML = h;
    }catch(e){
      $("llStats").innerHTML = "";
    }
  };

  window.__llLoad = async function(offset){
    var st = window.__loginlogs_state;
    if (typeof offset === "number") st.offset = offset;
    var el = $("llList");
    if (!el) return;
    el.innerHTML = "<div class=empty>加载中...</div>";
    try{
      var url = "/api/admin/login-logs?limit="+st.limit+"&offset="+st.offset;
      if (st.role) url += "&role="+encodeURIComponent(st.role);
      if (st.keyword) url += "&keyword="+encodeURIComponent(st.keyword);
      var d = await api(url);
      var items = d.items || [];
      if (!items.length){ el.innerHTML = "<div class=empty>暂无登录记录</div>"; return; }

      var h = "<div style='overflow-x:auto'><table style='width:100%;border-collapse:collapse;font-size:13px'>";
      h += "<thead><tr style='background:#f5f7fb;text-align:left'>";
      h += "<th style='padding:10px'>时间</th><th style='padding:10px'>角色</th>";
      h += "<th style='padding:10px'>账号</th><th style='padding:10px'>IP</th>";
      h += "<th style='padding:10px'>结果</th><th style='padding:10px'>说明</th>";
      h += "</tr></thead><tbody>";

      for (var i=0;i<items.length;i++){
        var r = items[i];
        var okTxt = r.ok
          ? "<span style='color:#22b573;font-weight:600'>成功</span>"
          : "<span style='color:#ff5b5b;font-weight:600'>失败</span>";
        h += "<tr style='border-top:1px solid #f0f2f7'>";
        h += "<td style='padding:9px;white-space:nowrap;color:#666'>"+fmtTime(r.created_at)+"</td>";
        h += "<td style='padding:9px'>"+roleTag(r.role)+"</td>";
        h += "<td style='padding:9px'><b>"+esc(r.username||"-")+"</b>"+(r.user_id?("<span style='color:#8a94a6;font-size:12px'> #"+r.user_id+"</span>"):"")+"</td>";
        h += "<td style='padding:9px;font-family:monospace;font-size:12.5px;color:#555'>"+esc(r.ip||"-")+"</td>";
        h += "<td style='padding:9px;white-space:nowrap'>"+okTxt+"</td>";
        h += "<td style='padding:9px;color:#8a94a6;font-size:12.5px'>"+esc(r.fail_reason||"")+"</td>";
        h += "</tr>";
      }
      h += "</tbody></table></div>";

      var total = d.total || 0;
      h += "<div style='display:flex;justify-content:space-between;align-items:center;margin-top:14px;font-size:13px;color:#8a94a6'>";
      h += "<span>共 "+total+" 条，当前第 "+(Math.floor(st.offset/st.limit)+1)+" 页</span>";
      h += "<span style='display:flex;gap:8px'>";
      if (st.offset > 0){
        h += "<button class='btn2' style='padding:7px 14px;font-size:13px' onclick='window.__llLoad("+(st.offset-st.limit)+")'>上一页</button>";
      }
      if (st.offset + st.limit < total){
        h += "<button class='btn2' style='padding:7px 14px;font-size:13px' onclick='window.__llLoad("+(st.offset+st.limit)+")'>下一页</button>";
      }
      h += "</span></div>";

      el.innerHTML = h;
    }catch(e){
      el.innerHTML = "<div class=empty>加载失败："+esc(e.message||"")+"</div>";
    }
  };

  window.__llExport = function(){
    var st = window.__loginlogs_state;
    var url = "/api/admin/login-logs/export";
    if (st.role) url += "?role="+encodeURIComponent(st.role);
    // 带 token 下载
    fetch(url, { headers: { "Authorization": "Bearer " + TK } })
      .then(function(r){ return r.blob(); })
      .then(function(b){
        var a = document.createElement("a");
        a.href = URL.createObjectURL(b);
        a.download = "login_logs.csv";
        document.body.appendChild(a); a.click(); a.remove();
        tt("已导出");
      })
      .catch(function(e){ tt("导出失败："+e.message); });
  };

  // 注册菜单
  if (typeof MENU !== "undefined" && MENU.push){
    var exists = MENU.filter(function(m){ return m.k === "loginlogs"; }).length > 0;
    if (!exists){
      // 插到"审核员管理"后面
      var pos = -1;
      for (var i=0;i<MENU.length;i++){ if (MENU[i].k === "reviewers"){ pos = i; break; } }
      var item = { k: "loginlogs", ic: "🕵️", t: "登录日志" };
      if (pos >= 0) MENU.splice(pos+1, 0, item); else MENU.push(item);
      if (typeof buildNav === "function") buildNav();
      console.log("登录日志菜单已注册");
    }
  }
})();
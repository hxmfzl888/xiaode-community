// 管理端：资源管理页（admin_resources.js）
(function(){
  if (typeof R === "undefined") { console.warn("R 未定义"); return; }

  var ST_TXT = { pending:"待审核", approved:"已通过", rejected:"已驳回" };

  function stTag(s){
    var c = s==="approved" ? "#22b573" : (s==="rejected" ? "#ff5b5b" : "#f5a623");
    var t = ST_TXT[s] || s;
    return "<span style='display:inline-block;padding:2px 9px;border-radius:999px;background:"+c+"1a;color:"+c+";font-size:12px;font-weight:600'>"+t+"</span>";
  }

  window.__res_state = { st:"", category:"", keyword:"", offset:0, limit:50 };

  R.resources = async function(){
    var st = window.__res_state;
    var h = "";
    h += "<div class=card>";
    h += "<div class=sec-title style='margin-top:0'>📁 资源管理</div>";
    h += "<div class=muted style='margin-bottom:12px'>用户上传的资源需审核后才能在 App 内公开。审核端入口：<a href='/review' target='_blank'>/review</a></div>";
    h += "<div id='resStats' style='margin-bottom:14px'></div>";
    h += "<div style='display:flex;gap:8px;flex-wrap:wrap;margin-bottom:14px'>";
    h += "<select id='resSt' onchange='window.__resChange()' style='padding:8px 12px;border:1px solid #e8ecf3;border-radius:9px'>";
    h += "<option value=''>全部状态</option><option value='pending'>待审核</option>";
    h += "<option value='approved'>已通过</option><option value='rejected'>已驳回</option></select>";
    h += "<input id='resCat' placeholder='分类' style='padding:8px 12px;border:1px solid #e8ecf3;border-radius:9px;width:110px'>";
    h += "<input id='resKw' placeholder='搜索资源名称/简介' style='padding:8px 12px;border:1px solid #e8ecf3;border-radius:9px;flex:1;min-width:130px' onkeydown=\"if(event.key==='Enter')window.__resSearch()\">";
    h += "<button class='btn2' style='padding:8px 16px;font-size:13px' onclick='window.__resSearch()'>查询</button>";
    h += "</div>";
    h += "<div id='resList'><div class=empty>加载中...</div></div>";
    h += "</div>";
    $("content").innerHTML = h;

    if (st.st) $("resSt").value = st.st;
    if (st.category) $("resCat").value = st.category;
    if (st.keyword) $("resKw").value = st.keyword;

    await window.__resLoadStats();
    await window.__resLoad(0);
  };

  window.__resChange = function(){
    window.__res_state.st = $("resSt").value;
    window.__res_state.category = $("resCat").value.trim();
    window.__res_state.keyword = $("resKw").value.trim();
    window.__resLoad(0);
  };

  window.__resSearch = function(){
    window.__res_state.st = $("resSt").value;
    window.__res_state.category = $("resCat").value.trim();
    window.__res_state.keyword = $("resKw").value.trim();
    window.__resLoad(0);
  };

  window.__resLoadStats = async function(){
    try{
      var s = await api("/api/admin/resources/stats");
      var h = "<div style='display:flex;gap:10px;flex-wrap:wrap'>";
      var cfg = [
        ["总资源", s.total||0, "#3b6ef5", "#eef3ff"],
        ["待审核", s.pending||0, "#f5a623", "#fff8ec"],
        ["已通过", s.approved||0, "#22b573", "#eefaf3"],
        ["已驳回", s.rejected||0, "#ff5b5b", "#fff1f1"],
        ["付费资源", s.paid||0, "#7c5cff", "#f3f0ff"]
      ];
      for (var i=0;i<cfg.length;i++){
        h += "<div style='flex:1;min-width:100px;background:"+cfg[i][3]+";border-radius:12px;padding:12px 14px'>";
        h += "<div style='font-size:22px;font-weight:800;color:"+cfg[i][2]+"'>"+cfg[i][1]+"</div>";
        h += "<div style='font-size:12.5px;color:#8a94a6;margin-top:2px'>"+cfg[i][0]+"</div></div>";
      }
      h += "</div>";
      $("resStats").innerHTML = h;
    }catch(e){}
  };

  window.__resLoad = async function(offset){
    var st = window.__res_state;
    if (typeof offset === "number") st.offset = offset;
    var el = $("resList");
    if (!el) return;
    el.innerHTML = "<div class=empty>加载中...</div>";
    try{
      var url = "/api/admin/resources?limit="+st.limit+"&offset="+st.offset;
      if (st.st) url += "&st="+encodeURIComponent(st.st);
      if (st.category) url += "&category="+encodeURIComponent(st.category);
      if (st.keyword) url += "&keyword="+encodeURIComponent(st.keyword);
      var d = await api(url);
      var items = d.items || [];
      if (!items.length){ el.innerHTML = "<div class=empty>暂无资源</div>"; return; }

      var h = "";
      for (var i=0;i<items.length;i++){
        var r = items[i];
        h += "<div class=item>";

        // 头部信息
        h += "<div class=m>#" + r.id + " · 上传者:" + esc(r.uploader_name||r.uploader_id||"?") +
             " (ID" + r.uploader_id + ") · " + fmtTime(r.created_at) + " " + stTag(r.status) + "</div>";

        // 封面 + 标题
        var cover = r.cover ? "<img src='"+esc(r.cover)+"' style='width:64px;height:64px;border-radius:10px;object-fit:cover;flex:none'>" : "";
        h += "<div style='display:flex;gap:10px;align-items:flex-start;margin:6px 0'>" + cover;
        h += "<div style='flex:1;min-width:0'>";
        h += "<div style='font-weight:700;font-size:15px'>" + esc(r.name||"(无名称)") + "</div>";
        h += "<div class=muted style='margin-top:2px'>分类：" + esc(r.category||"-") +
             " · " + (r.res_type==="file" ? "直传文件" : "下载链接") +
             (r.file_size ? " · " + (r.file_size/1048576).toFixed(2)+"MB" : "") + "</div>";
        h += "<div class=muted>" + (r.is_paid ? "<span style='color:#f5a623;font-weight:600'>付费 " + r.price + " 金币</span>" : "<span style='color:#22b573'>免费</span>") + "</div>";
        h += "</div></div>";

        if (r.summary) h += "<div style='font-size:13px;color:#666;margin-top:4px'>" + esc(r.summary) + "</div>";
        if (r.description) h += "<div style='font-size:12.5px;color:#999;margin-top:2px'>" + esc(r.description).slice(0,160) + "</div>";
        if (r.download_url) h += "<div class=muted style='word-break:break-all;margin-top:4px'>链接：" + esc(r.download_url) + "</div>";

        // 截图
        var shots = "";
        try {
          var arr = JSON.parse(r.screenshots || "[]");
          shots = arr.map(function(u){ return "<img src='"+u+"' style='width:66px;height:66px;border-radius:8px;object-fit:cover;margin:4px 4px 0 0'>"; }).join("");
        } catch(e){}
        if (shots) h += "<div style='margin-top:6px'>" + shots + "</div>";

        if (r.reject_reason) h += "<div style='font-size:12.5px;color:#e05555;margin-top:4px'>驳回原因：" + esc(r.reject_reason) + "</div>";

        // 数据
        h += "<div class=muted style='margin-top:6px'>浏览 " + r.view_count + " · 下载 " + r.download_count + " · 打赏 " + r.tip_total + " 金币</div>";

        // 操作
        h += "<div style='margin-top:9px;display:flex;gap:6px;flex-wrap:wrap'>";
        if (r.status !== "approved")
          h += "<button class='btn2 k' style='padding:6px 12px;font-size:13px' onclick='window.__resR("+r.id+",1)'>通过</button>";
        if (r.status !== "rejected")
          h += "<button class='btn2 r' style='padding:6px 12px;font-size:13px' onclick='window.__resR("+r.id+",0)'>驳回</button>";
        h += "<button class='btn2' style='padding:6px 12px;font-size:13px' onclick='window.__resEdit("+r.id+")'>编辑</button>";
        h += "<button class='btn2' style='padding:6px 12px;font-size:13px' onclick='window.__resDel("+r.id+")'>删除</button>";
        h += "</div></div>";
      }

      var total = d.total || 0;
      h += "<div style='display:flex;justify-content:space-between;align-items:center;margin-top:14px;font-size:13px;color:#8a94a6'>";
      h += "<span>共 " + total + " 条，第 " + (Math.floor(st.offset/st.limit)+1) + " 页</span><span style='display:flex;gap:8px'>";
      if (st.offset > 0)
        h += "<button class='btn2' style='padding:7px 14px;font-size:13px' onclick='window.__resLoad("+(st.offset-st.limit)+")'>上一页</button>";
      if (st.offset + st.limit < total)
        h += "<button class='btn2' style='padding:7px 14px;font-size:13px' onclick='window.__resLoad("+(st.offset+st.limit)+")'>下一页</button>";
      h += "</span></div>";

      el.innerHTML = h;
    }catch(e){
      el.innerHTML = "<div class=empty>加载失败：" + esc(e.message||"") + "</div>";
    }
  };

  window.__resR = async function(id, ok){
    var body = { action: ok ? "approved" : "rejected" };
    if (!ok){
      var r = prompt("驳回原因（会通知上传者，建议写明修改建议）：");
      if (r === null) return;
      body.reason = r;
    }
    try{
      await api("/api/admin/resources/"+id+"/review", { method:"POST", body: body });
      tt(ok ? "已通过" : "已驳回");
      window.__resLoad();
      window.__resLoadStats();
    }catch(e){ tt(errMsg ? errMsg(e) : e.message); }
  };

  window.__resDel = async function(id){
    if (!confirm("确认删除该资源？相关打赏/购买记录也会删除，不可恢复！")) return;
    try{
      await api("/api/admin/resources/"+id, { method:"DELETE" });
      tt("已删除");
      window.__resLoad();
      window.__resLoadStats();
    }catch(e){ tt(errMsg ? errMsg(e) : e.message); }
  };

  window.__resEdit = async function(id){
    var d;
    try{ d = await api("/api/admin/resources/"+id); }
    catch(e){ tt("加载失败"); return; }

    var h = "<div class=mask on id=resMask><div class=modal style='max-width:600px'>";
    h += "<div class=sec-title style='margin-top:0'>✏️ 编辑资源 #"+d.id+"</div>";
    h += "<div class=fld><label>名称</label><input id=re_name value=\""+esc(d.name)+"\"></div>";
    h += "<div class=fld><label>简介</label><input id=re_sum value=\""+esc(d.summary)+"\"></div>";
    h += "<div class=fld><label>分类</label><input id=re_cat value=\""+esc(d.category)+"\"></div>";
    h += "<div class=fld><label>下载链接</label><input id=re_url value=\""+esc(d.download_url)+"\"></div>";
    h += "<div class=fld><label>封面图地址</label><input id=re_cover value=\""+esc(d.cover)+"\"></div>";
    h += "<div class=fld><label>详细说明</label><textarea id=re_desc rows=4 style='width:100%;padding:9px 11px;border:1px solid #e8ecf3;border-radius:9px;font-family:inherit'>"+esc(d.description)+"</textarea></div>";
    h += "<div class=fld style='display:flex;align-items:center;gap:10px'>";
    h += "<label style='display:flex;align-items:center;gap:6px'><input type=checkbox id=re_paid "+(d.is_paid?"checked":"")+"> 付费资源</label>";
    h += "<label style='display:flex;align-items:center;gap:6px'>价格 <input id=re_price type=number value='"+(d.price||0)+"' style='width:80px;padding:6px 9px;border:1px solid #e8ecf3;border-radius:8px'></label>";
    h += "</div>";
    h += "<div style='display:flex;gap:8px;justify-content:flex-end;margin-top:16px'>";
    h += "<button class='btn2' style='padding:9px 18px' onclick='document.getElementById(\"resMask\").remove()'>取消</button>";
    h += "<button class='btn2 k' style='padding:9px 18px' onclick='window.__resSave("+d.id+")'>保存</button>";
    h += "</div></div></div>";
    document.body.insertAdjacentHTML("beforeend", h);
  };

  window.__resSave = async function(id){
    var body = {
      name: $("re_name").value.trim(),
      summary: $("re_sum").value.trim(),
      category: $("re_cat").value.trim(),
      download_url: $("re_url").value.trim(),
      cover: $("re_cover").value.trim(),
      description: $("re_desc").value,
      is_paid: $("re_paid").checked,
      price: parseInt($("re_price").value || "0", 10)
    };
    try{
      await api("/api/admin/resources/"+id, { method:"PUT", body: body });
      tt("已保存");
      var m = $("resMask"); if (m) m.remove();
      window.__resLoad();
    }catch(e){ tt(errMsg ? errMsg(e) : e.message); }
  };

  // 注册菜单（插到"审核管理"之后）
  if (typeof MENU !== "undefined" && MENU.push){
    var exists = MENU.filter(function(m){ return m.k === "resources"; }).length > 0;
    if (!exists){
      var pos = -1;
      for (var i=0;i<MENU.length;i++){ if (MENU[i].k === "audit"){ pos = i; break; } }
      var item = { k:"resources", ic:"📁", t:"资源管理" };
      if (pos >= 0) MENU.splice(pos+1, 0, item); else MENU.push(item);
      if (typeof buildNav === "function") buildNav();
      console.log("资源管理菜单已注册");
    }
  }
})();
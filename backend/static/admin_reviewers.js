R.reviewers = async function () {
  var h = [];
  h.push('<div class="card">');
  h.push('<div class="sec-title" style="margin-top:0">👤 审核员管理</div>');
  h.push('<div class="muted" style="margin-bottom:12px">审核员使用独立账号登录 <a href="/review" target="_blank">/review</a> 审核端，审核时看不到用户昵称和头像，只能看到用户 ID。</div>');
  h.push('<div class="row">');
  h.push('<input id="nvUser" placeholder="登录账号" style="padding:8px 10px;border:1px solid #d6dcea;border-radius:8px;width:150px">');
  h.push('<input id="nvNick" placeholder="显示昵称" style="padding:8px 10px;border:1px solid #d6dcea;border-radius:8px;width:150px">');
  h.push('<input id="nvPass" placeholder="密码(至少6位)" style="padding:8px 10px;border:1px solid #d6dcea;border-radius:8px;width:160px">');
  h.push('<button class="btn2" onclick="window.addRev()">新增审核员</button>');
  h.push('</div>');
  h.push('<div id="rvList" style="margin-top:14px"></div>');
  h.push('</div>');
  $("content").innerHTML = h.join("");
  loadRevs();
};

async function loadRevs() {
  var el = $("rvList");
  if (!el) return;
  el.innerHTML = '<div class="empty">加载中...</div>';
  try {
    var list = await api("/api/admin/reviewers");
    if (!list.length) {
      el.innerHTML = '<div class="empty">还没有审核员账号，请在上方新增</div>';
      return;
    }
    var out = [];
    for (var i = 0; i < list.length; i++) {
      var r = list[i];
      var st = r.active
        ? '<span style="color:#22b573;font-weight:600">启用</span>'
        : '<span style="color:#d33;font-weight:600">已停用</span>';
      var ll = r.last_login ? r.last_login.replace("T", " ").slice(0, 16) : "从未登录";
      var cr = r.created_at ? r.created_at.slice(0, 10) : "-";
      var btnLabel = r.active ? "停用" : "启用";
      out.push('<div class="item">');
      out.push('<div style="display:flex;justify-content:space-between;align-items:center">');
      out.push('<div><b>' + esc(r.nickname || r.username) + '</b> <span class="muted">@' + esc(r.username) + '</span> ' + st + '</div>');
      out.push('<div class="muted">#' + r.id + '</div>');
      out.push('</div>');
      out.push('<div class="muted">创建 ' + cr + ' ｜ 最后登录 ' + ll + '</div>');
      out.push('<div style="margin-top:8px">');
      out.push('<button class="btn2" onclick="window.toggleRev(' + r.id + ')">' + btnLabel + '</button> ');
      out.push('<button class="btn2" onclick="window.resetRev(' + r.id + ')">重置密码</button> ');
      out.push('<button class="btn2" onclick="window.delRev(' + r.id + ',\'' + esc(r.username) + '\')">删除</button>');
      out.push('</div></div>');
    }
    el.innerHTML = out.join("");
  } catch (e) {
    el.innerHTML = '<div class="empty">加载失败：' + esc(String(e.message || e)) + '</div>';
  }
}

window.addRev = async function () {
  var u = $("nvUser").value.trim();
  var nk = $("nvNick").value.trim();
  var pw = $("nvPass").value;
  if (!u || !pw) return tt("账号和密码必填");
  if (pw.length < 6) return tt("密码至少6位");
  try {
    await api("/api/admin/reviewers", { method: "POST", body: { username: u, password: pw, nickname: nk } });
    tt("审核员已创建");
    $("nvUser").value = "";
    $("nvNick").value = "";
    $("nvPass").value = "";
    loadRevs();
  } catch (e) {
    tt(errMsg(e));
  }
};

window.toggleRev = async function (id) {
  try {
    var r = await api("/api/admin/reviewers/" + id + "/toggle", { method: "POST" });
    tt(r.active ? "已启用" : "已停用");
    loadRevs();
  } catch (e) {
    tt(errMsg(e));
  }
};

window.resetRev = async function (id) {
  var p = prompt("输入新密码（至少6位）：");
  if (!p) return;
  if (p.length < 6) return tt("密码至少6位");
  try {
    await api("/api/admin/reviewers/" + id + "/reset-password?new_password=" + encodeURIComponent(p), { method: "POST" });
    tt("密码已重置");
  } catch (e) {
    tt(errMsg(e));
  }
};

window.delRev = async function (id, un) {
  if (!confirm("确认删除审核员 [" + un + "] ？此操作不可恢复")) return;
  try {
    await api("/api/admin/reviewers/" + id, { method: "DELETE" });
    tt("已删除");
    loadRevs();
  } catch (e) {
    tt(errMsg(e));
  }
};

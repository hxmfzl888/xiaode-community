R.splash = async function () {
  var h = [];
  h.push('<div class="card">');
  h.push('<div class="sec-title" style="margin-top:0">📺 开屏广告</div>');
  h.push('<div class="muted" style="margin-bottom:14px">App 启动时全屏展示，右上角可按设定秒数跳过；点击广告跳外部浏览器。</div>');

  h.push('<div class="row" style="align-items:flex-start;gap:12px">');
  h.push('<div style="flex:1;min-width:260px">');
  h.push('<div style="font-weight:600;margin-bottom:6px">1. 上传广告图</div>');
  h.push('<input type="file" id="spFile" accept="image/*" style="padding:8px;border:1px solid #d6dcea;border-radius:8px;width:100%">');
  h.push('<div id="spPrev" style="margin-top:8px"></div>');
  h.push('</div>');
  h.push('<div style="flex:1;min-width:260px">');
  h.push('<div style="font-weight:600;margin-bottom:6px">2. 广告信息</div>');
  h.push('<input id="spTitle" placeholder="备注名（方便自己识别）" style="width:100%;padding:9px 12px;border:1px solid #d6dcea;border-radius:8px;margin-bottom:8px">');
  h.push('<input id="spLink" placeholder="点击跳转链接（如 https://...）" style="width:100%;padding:9px 12px;border:1px solid #d6dcea;border-radius:8px;margin-bottom:8px">');
  h.push('<input id="spSkip" type="number" value="3" min="0" max="15" placeholder="跳过秒数" style="width:100%;padding:9px 12px;border:1px solid #d6dcea;border-radius:8px;margin-bottom:8px">');
  h.push('<input id="spSort" type="number" value="0" placeholder="权重（大在前）" style="width:100%;padding:9px 12px;border:1px solid #d6dcea;border-radius:8px;margin-bottom:8px">');
  h.push('<button class="btn2" onclick="window.spCreate()">创建广告</button>');
  h.push('</div>');
  h.push('</div>');

  h.push('<div id="spList" style="margin-top:18px"></div>');
  h.push('</div>');
  $("content").innerHTML = h.join("");

  $("spFile").onchange = function () {
    var f = this.files && this.files[0];
    if (!f) return;
    var url = URL.createObjectURL(f);
    $("spPrev").innerHTML = '<img src="' + url + '" style="max-width:200px;max-height:200px;border-radius:10px;border:1px solid #eef1f8">';
  };

  loadSplashAds();
};

async function loadSplashAds() {
  var el = $("spList");
  if (!el) return;
  el.innerHTML = '<div class="empty">加载中...</div>';
  try {
    var list = await api("/api/splash/admin/list");
    if (!list.length) {
      el.innerHTML = '<div class="empty">还没有开屏广告，请在上方创建</div>';
      return;
    }
    var out = [];
    for (var i = 0; i < list.length; i++) {
      var a = list[i];
      var st = a.enabled
        ? '<span style="color:#22b573;font-weight:600">启用中</span>'
        : '<span style="color:#d33;font-weight:600">已停用</span>';
      out.push('<div class="item">');
      out.push('<div style="display:flex;gap:12px;align-items:flex-start">');
      out.push('<img src="' + esc(a.image) + '" style="width:76px;height:130px;object-fit:cover;border-radius:8px;border:1px solid #eef1f8">');
      out.push('<div style="flex:1">');
      out.push('<div style="font-weight:600">' + esc(a.title || ('广告 #' + a.id)) + ' ' + st + '</div>');
      out.push('<div class="muted">跳过 ' + a.skip_seconds + ' 秒 ｜ 权重 ' + a.sort + '</div>');
      out.push('<div class="muted">链接：' + esc(a.link || '（无，点击不跳转）') + '</div>');
      out.push('<div class="muted">展示 ' + a.view_count + ' 次 ｜ 点击 ' + a.click_count + ' 次</div>');
      out.push('<div style="margin-top:8px">');
      out.push('<button class="btn2" onclick="window.spToggle(' + a.id + ')">' + (a.enabled ? '停用' : '启用') + '</button> ');
      out.push('<button class="btn2" onclick="window.spEdit(' + a.id + ')">改跳过秒数</button> ');
      out.push('<button class="btn2" onclick="window.spDel(' + a.id + ')">删除</button>');
      out.push('</div></div></div></div>');
    }
    el.innerHTML = out.join("");
  } catch (e) {
    el.innerHTML = '<div class="empty">加载失败：' + esc(String(e.message || e)) + '</div>';
  }
}

window.spCreate = async function () {
  var f = $("spFile").files && $("spFile").files[0];
  if (!f) return tt("请先选择广告图");
  var fd = new FormData();
  fd.append("file", f);
  var btn = event && event.target;
  tt("上传中...");
  try {
    var res = await fetch("/api/splash/admin/upload", {
      method: "POST",
      headers: { "Authorization": "Bearer " + (typeof TK !== "undefined" ? TK : "") },
      body: fd
    });
    var up = await res.json();
    if (!res.ok) throw new Error(up.detail || "上传失败");
    await api("/api/splash/admin/create", {
      method: "POST",
      body: {
        image: up.url,
        title: $("spTitle").value.trim(),
        link: $("spLink").value.trim(),
        skip_seconds: parseInt($("spSkip").value || "3", 10),
        sort: parseInt($("spSort").value || "0", 10),
        enabled: true
      }
    });
    tt("广告已创建");
    $("spTitle").value = "";
    $("spLink").value = "";
    $("spFile").value = "";
    $("spPrev").innerHTML = "";
    loadSplashAds();
  } catch (e) {
    tt(String(e.message || e));
  }
};

window.spToggle = async function (id) {
  try {
    var r = await api("/api/splash/admin/" + id + "/toggle", { method: "POST" });
    tt(r.enabled ? "已启用" : "已停用");
    loadSplashAds();
  } catch (e) { tt(String(e.message || e)); }
};

window.spEdit = async function (id) {
  var v = prompt("设置跳过秒数（0-15）：", "3");
  if (v === null) return;
  var n = parseInt(v, 10);
  if (isNaN(n) || n < 0 || n > 15) return tt("请输入 0-15 的数字");
  try {
    await api("/api/splash/admin/" + id + "/update", { method: "POST", body: { skip_seconds: n } });
    tt("已更新");
    loadSplashAds();
  } catch (e) { tt(String(e.message || e)); }
};

window.spDel = async function (id) {
  if (!confirm("确认删除该开屏广告？")) return;
  try {
    await api("/api/splash/admin/" + id, { method: "DELETE" });
    tt("已删除");
    loadSplashAds();
  } catch (e) { tt(String(e.message || e)); }
};
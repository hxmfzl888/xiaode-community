async function loadTools(){
var st=document.getElementById('tlSt').value;
var l=await rq('/api/reviewer/tools?st='+st);
var el=document.getElementById('tList');
if(!l.length){el.innerHTML='<div class=muted>暂无工具</div>';return;}
var h='';
for(var i=0;i<l.length;i++){
var t=l[i];
h+='<div class=item>'+tg(t.status)+' <b>'+esc(t.name)+'</b> <span class=muted>#'+t.id+' 上传者:'+t.author_id+'</span><div class=muted>'+esc(t.intro)+'</div><div class=muted>'+esc(t.url)+'</div>';
if(t.status==='pending'){
h+='<div style=margin-top:8px><button class=k onclick=tv('+t.id+',1)>通过</button> <button class=r onclick=tv('+t.id+',0)>驳回</button></div>';
}
h+='</div>';
}
el.innerHTML=h;
}
async function tv(id,ok){
var rs='';
if(!ok){rs=prompt('驳回原因')||'';}
await rq('/api/reviewer/tools/'+id+'/review',{method:'POST',body:JSON.stringify({approve:ok===1,reason:rs})});
tt(ok===1?'已通过':'已驳回');
loadTools();loadStats();
}

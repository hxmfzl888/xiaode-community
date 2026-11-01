async function loadAuditT(){
var list=await api('/api/admin/tools?st=pending');
if(!list.length){$('alist').innerHTML='<div class=empty>没有待审工具</div>';return;}
$('alist').innerHTML=list.map(function(t){
return [
  '<div class=item>',
  '<div class=m>#'+t.id+' 上传者:'+esc(t.uploader_name||t.uploader_id)+'</div>',
  '<div style=font-weight:600>'+esc(t.name)+'</div>',
  '<div class=muted>'+esc(t.intro||"")+'</div>',
  '<div class=muted>链接: '+esc(t.url)+'</div>',
  '<div style=margin-top:8px>',
  '<button class=btn2 onclick=window.rvTool('+t.id+',1)>通过</button> ',
  '<button class=btn2 onclick=window.rvTool('+t.id+',0)>驳回</button> ',
  '<button class=btn2 onclick=window.delTool('+t.id+')>删除</button>',
  '</div></div>'
].join('');
}).join('');
}
window.rvTool=async function(id,ok){
var body={action:ok?'approved':'rejected'};
if(!ok){var r=prompt('驳回理由:');if(r===null)return;body.reason=r;}
try{await api('/api/admin/tools/'+id+'/review',{method:'POST',body:body});tt(ok?'已通过':'已驳回');loadAuditT();}catch(e){tt(String(e));}
};
window.delTool=async function(id){
if(!confirm('确认删除?'))return;
try{await api('/api/admin/tools/'+id,{method:'DELETE'});tt('已删除');loadAuditT();}catch(e){tt(String(e));}
};

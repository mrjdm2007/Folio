import {useCallback,useEffect,useRef,useState} from "react";
import {FilePlus2,FileText,Download,Trash2,FileCheck2,HardDrive} from "lucide-react";
import {deleteFile,getFiles,putFile,type StoredFile} from "./fileStore";

const formatSize=(bytes:number)=>bytes<1024*1024?(bytes/1024).toFixed(0)+" KB":(bytes/1024/1024).toFixed(1)+" MB";
const formatDate=(timestamp?:number)=>timestamp?new Date(timestamp).toLocaleDateString(undefined,{day:"numeric",month:"short",year:"numeric"}):"Previously uploaded";

export default function Documents(){
 const input=useRef<HTMLInputElement>(null);
 const [files,setFiles]=useState<StoredFile[]>([]);
 const [busy,setBusy]=useState(false);
 const [message,setMessage]=useState("");
 const [error,setError]=useState("");
 const refresh=useCallback(async()=>{const all=await getFiles();setFiles(all.filter(f=>f.name.toLowerCase().endsWith(".pdf")||f.mime==="application/pdf").sort((a,b)=>(b.uploadedAt||0)-(a.uploadedAt||0)))},[]);
 useEffect(()=>{refresh().catch(()=>setError("Could not load your PDF library. Check browser storage permissions."))},[refresh]);
 const upload=async(selected:FileList|null)=>{if(!selected?.length)return;setBusy(true);setError("");setMessage("");let added=0;try{for(const file of Array.from(selected)){if(file.type!=="application/pdf"&&!file.name.toLowerCase().endsWith(".pdf")){setError(file.name+" is not a PDF and was skipped.");continue}if(file.size===0){setError(file.name+" is empty and was skipped.");continue}let name=file.name;const existing=new Set((await getFiles()).map(x=>x.name));if(existing.has(name)){const dot=name.lastIndexOf("."),stem=dot>0?name.slice(0,dot):name,ext=dot>0?name.slice(dot):".pdf";let n=2;while(existing.has(stem+" ("+n+")"+ext))n++;name=stem+" ("+n+")"+ext}await putFile(name,file);added++}await refresh();if(added)setMessage(added+" PDF"+(added===1?" uploaded":"s uploaded")+" successfully.");}catch(e){setError("Upload failed: "+(e as Error).message)}finally{setBusy(false);if(input.current)input.current.value=""}};
 const download=async(file:StoredFile)=>{const url=URL.createObjectURL(file.blob),a=document.createElement("a");a.href=url;a.download=file.name;a.click();window.setTimeout(()=>URL.revokeObjectURL(url),1000)};
 const remove=async(file:StoredFile)=>{if(!window.confirm("Delete "+file.name+" from your Folio PDF library?"))return;try{await deleteFile(file.name);await refresh();setMessage("PDF deleted from this browser.")}catch(e){setError("Could not delete PDF: "+(e as Error).message)}};
 const total=files.reduce((sum,f)=>sum+f.blob.size,0);
 return <section className="workspace"><div className="page-heading"><div><div className="eyebrow">YOUR FILE LIBRARY</div><h1>PDF uploads</h1><p>Keep your reference PDFs together in Folio. Files stay in this browser until you export a backup or delete them.</p></div><button className="primary-button" onClick={()=>input.current?.click()} disabled={busy}><FilePlus2 size={16}/>{busy?"Uploading…":"Upload PDFs"}</button><input ref={input} hidden type="file" accept="application/pdf,.pdf" multiple onChange={e=>upload(e.target.files)}/></div>
 <div className="stats-grid"><div className="stat-card"><small>Uploaded PDFs</small><strong>{files.length}</strong></div><div className="stat-card"><small>Storage used</small><strong>{formatSize(total)}</strong></div></div>
 {message&&<p className="success" role="status">{message}</p>}{error&&<p className="error" role="alert">{error}</p>}
 <div className="pdf-library">{files.map(file=><article className="pdf-library-row" key={file.name}><div className="pdf-file-icon"><FileText size={20}/></div><div className="pdf-file-info"><strong title={file.name}>{file.name}</strong><small>{formatSize(file.blob.size)} · Uploaded {formatDate(file.uploadedAt)}</small></div><div className="pdf-file-actions"><button className="icon-button" title={"Download "+file.name} onClick={()=>download(file)}><Download size={17}/></button><button className="icon-button danger" title={"Delete "+file.name} onClick={()=>remove(file)}><Trash2 size={17}/></button></div></article>)}
 {!files.length&&<div className="pdf-empty"><div className="pdf-empty-icon"><FileCheck2 size={25}/></div><h3>No PDFs uploaded yet</h3><p>Upload lecture notes, datasheets, manuals or other PDF references. Folio stores them locally in this browser.</p><button className="primary-button" onClick={()=>input.current?.click()}><FilePlus2 size={16}/>Choose PDF files</button></div>}</div>
 <p className="pdf-storage-note"><HardDrive size={14}/> PDFs are stored locally on this device and included in Folio JSON backups. They are not automatically synced to Firebase cloud storage.</p></section>
}

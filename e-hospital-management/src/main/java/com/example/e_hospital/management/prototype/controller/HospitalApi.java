package com.example.e_hospital.management.prototype.controller;

import com.example.e_hospital.management.prototype.data.ModuleCatalog;
import com.example.e_hospital.management.prototype.service.HospitalService;
import com.example.e_hospital.management.prototype.service.CsvReader;
import com.example.e_hospital.management.prototype.model.RecordPage;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController @RequestMapping("/api")
public class HospitalApi {
    private final HospitalService service;private final ModuleCatalog catalog;
    public HospitalApi(HospitalService service,ModuleCatalog catalog){this.service=service;this.catalog=catalog;}
    private String actor(HttpSession session){return String.valueOf(session.getAttribute("demoUser"));}
    @GetMapping("/catalog") Object catalog(){return catalog.all();}
    @GetMapping("/references") Object references(){return service.references();}
    @GetMapping("/dashboard") Object dashboard(@RequestParam(defaultValue="") String from,@RequestParam(defaultValue="") String to,HttpSession session){var result=new LinkedHashMap<>(service.dashboard(from,to));String role=String.valueOf(session.getAttribute("demoRole"));if(!DemoAccess.admin(role))result.keySet().removeIf(k->!k.equals("chart")&&!DemoAccess.modules(role).contains(k));return result;}
    @GetMapping("/records/{module}") RecordPage list(@PathVariable String module,@RequestParam(defaultValue="")String search,@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="")String filter,@RequestParam(defaultValue="")String from,@RequestParam(defaultValue="")String to,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="8")int size,@RequestParam(defaultValue="")String sort,@RequestParam(defaultValue="asc")String dir){return service.list(module,search,status,filter,from,to,page,size,sort,dir);}
    @GetMapping("/records/{module}/{id}") Object detail(@PathVariable String module,@PathVariable String id,HttpSession session){var result=new LinkedHashMap<>(service.details(module,id));String role=String.valueOf(session.getAttribute("demoRole"));if(!DemoAccess.admin(role)){
        @SuppressWarnings("unchecked") var related=new LinkedHashMap<>((Map<String,Object>)result.get("related"));related.keySet().removeIf(k->!DemoAccess.modules(role).contains(k));result.put("related",related);
        if(!List.of("Doctor","Nurse").contains(role)&&List.of("patients","appointments","visits","queues").contains(module)){result.put("timeline",List.of());result.put("files",List.of());}
        }return result;}
    @PostMapping("/records/{module}") Object create(@PathVariable String module,@RequestBody Map<String,String> values,HttpSession session){return service.save(module,null,values,actor(session));}
    @PutMapping("/records/{module}/{id}") Object edit(@PathVariable String module,@PathVariable String id,@RequestBody Map<String,String> values,HttpSession session){return service.save(module,id,values,actor(session));}
    @DeleteMapping("/records/{module}/{id}") Object delete(@PathVariable String module,@PathVariable String id,HttpSession session){service.delete(module,id,actor(session));return Map.of("message","Record deleted");}
    @PostMapping("/records/{module}/{id}/actions") Object action(@PathVariable String module,@PathVariable String id,@RequestBody Map<String,String> values,HttpSession session){return service.transition(module,id,values.getOrDefault("action",""),values,actor(session));}
    @GetMapping("/export/{module}") ResponseEntity<byte[]> export(@PathVariable String module,@RequestParam(defaultValue="")String search,@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="")String filter,@RequestParam(defaultValue="")String from,@RequestParam(defaultValue="")String to){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+catalog.get(module).key()+"-demo.csv\"").contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(service.export(module,search,status,filter,from,to));}
    @PostMapping("/records/{module}/{id}/files") Object upload(@PathVariable String module,@PathVariable String id,@RequestParam MultipartFile file,HttpSession session)throws Exception {return Map.of("id",service.attach(module,id,Objects.requireNonNullElse(file.getOriginalFilename(),"document"),file.getBytes(),Objects.requireNonNullElse(file.getContentType(),""),actor(session)));}
    @GetMapping("/files/{id}") ResponseEntity<byte[]> file(@PathVariable String id,@RequestParam(defaultValue="false")boolean inline,HttpSession session){var f=service.attachment(id);String role=String.valueOf(session.getAttribute("demoRole"));String module=(String)f.get("module");if(!DemoAccess.admin(role)&&!module.equals("profile")&&!DemoAccess.modules(role).contains(module))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Document access is unavailable for this role.");String type=(String)f.get("contentType");return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.builder(inline&&type.startsWith("image/")?"inline":"attachment").filename((String)f.get("name")).build().toString()).contentType(MediaType.parseMediaType(type)).body((byte[])f.get("bytes"));}
    @PostMapping("/role") Object role(@RequestBody Map<String,String> body,HttpSession session){if("telegram".equals(session.getAttribute("authSource")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Linked Telegram roles cannot be changed from the browser.");String role=body.get("role");if(!WorkspaceController.ROLES.contains(role))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a demo role.");session.setAttribute("demoRole",role);return Map.of("role",role);}
    @GetMapping("/profile") Object profile(HttpSession session){Object p=session.getAttribute("profile");return p!=null?p:Map.of("name","Alex Morgan","email",actor(session),"phone","+1 202 555 0100","title","Hospital administrator","bio","Supporting better care, every day.");}
    @PostMapping("/profile") Object profile(@RequestBody Map<String,String> body,HttpSession session){if(body.getOrDefault("name","").isBlank()||!body.getOrDefault("email","").matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter your name and a valid email.");Map<String,String> p=new LinkedHashMap<>();for(String key:List.of("name","email","phone","title","bio","avatar"))p.put(key,body.getOrDefault(key,""));session.setAttribute("profile",p);return p;}
    @PostMapping("/password-preview") Object password(@RequestBody Map<String,String> body){if(body.getOrDefault("password","").length()<12||!body.get("password").equals(body.get("confirm")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use at least 12 characters and matching passwords.");return Map.of("message","Password form validated. Demo credentials are not stored or changed.");}
    @PostMapping("/import/medicines") Object importMedicines(@RequestParam MultipartFile file,@RequestParam(defaultValue="false")boolean preview,HttpSession session)throws Exception {
        var rows=CsvReader.read(new String(file.getBytes(),java.nio.charset.StandardCharsets.UTF_8));return preview?Map.of("count",rows.size(),"rows",rows.stream().limit(5).toList()):service.importMedicines(rows,actor(session));
    }
    @GetMapping("/import/medicines/template") ResponseEntity<byte[]> importTemplate(){return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=medicine-import-template.csv").contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body("name,generic,category,brand,unit,strength,price,manufacturer,notes\r\nDemo medicine,Demo ingredient,Analgesic,CarePlus Essentials,Tablet,500 mg,2.50,MedSource Manufacturing,Synthetic catalog example\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    @RestControllerAdvice static class ApiErrors {
        @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> bad(ResponseStatusException ex){return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message",Objects.requireNonNullElse(ex.getReason(),"Request failed")));}
        @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<?> large(){return ResponseEntity.badRequest().body(Map.of("message","Choose a file no larger than 5 MB."));}
    }
}

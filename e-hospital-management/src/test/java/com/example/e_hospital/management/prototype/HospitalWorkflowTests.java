package com.example.e_hospital.management.prototype;

import com.example.e_hospital.management.prototype.data.ModuleCatalog;
import com.example.e_hospital.management.prototype.service.HospitalService;
import com.example.e_hospital.management.prototype.service.CsvReader;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest @AutoConfigureMockMvc
class HospitalWorkflowTests {
    @Autowired HospitalService service;@Autowired ModuleCatalog catalog;@Autowired MockMvc mvc;
    MockHttpSession session(){var s=new MockHttpSession();s.setAttribute("demoUser","qa@example.test");s.setAttribute("demoRole","Administrator");return s;}
    @Test void allModulePagesAndEndpointsRender()throws Exception {
        mvc.perform(get("/ui/app.js")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("function hospital()")));
        mvc.perform(get("/ui/app.css")).andExpect(status().isOk());
        mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Demonstration login")));
        mvc.perform(get("/").session(session())).andExpect(status().isOk());
        assertThat(catalog.all().stream().filter(m->m.parent().isBlank()).toList()).hasSize(25);
        for(var module:catalog.all()){
            mvc.perform(get("/"+module.key()).session(session())).andExpect(status().isOk());
            mvc.perform(get("/api/records/"+module.key()).session(session())).andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());
            var row=service.list(module.key(),"","","","","",1,1).items().getFirst();
            mvc.perform(get("/api/records/"+module.key()+"/"+row.get("id")).session(session())).andExpect(status().isOk()).andExpect(jsonPath("$.record.id").value(row.get("id")));
        }
        for(String page:List.of("profile","appearance","change-password","queue-display"))mvc.perform(get("/"+page).session(session())).andExpect(status().isOk());
    }
    @Test void crudValidatesAndPreservesSelectedRecord(){
        var data=new LinkedHashMap<String,String>();data.put("name","QA Synthetic Patient");data.put("gender","Other");data.put("dateOfBirth","1995-01-01");data.put("phone","+1 202 555 0199");
        var created=service.save("patients",null,data,"QA");String id=created.get("id");
        assertThat(service.list("patients","QA Synthetic","","","","",1,8).total()).isEqualTo(1);
        created.put("name","QA Updated Patient");var updated=service.save("patients",id,created,"QA");assertThat(updated.get("name")).isEqualTo("QA Updated Patient");
        assertThatThrownBy(()->service.save("patients",id,created,"QA")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("changed");
        service.delete("patients",id,"QA");assertThatThrownBy(()->service.get("patients",id)).isInstanceOf(ResponseStatusException.class);
        data.put("dateOfBirth",LocalDate.now().plusDays(1).toString());assertThatThrownBy(()->service.save("patients",null,data,"QA")).hasMessageContaining("future");
    }
    @Test void filteringPaginationAndCsvUseCurrentRecords(){
        var page1=service.list("patients","","","","","",1,5);var page2=service.list("patients","","","","","",2,5);
        assertThat(page1.items()).hasSize(5);assertThat(page1.items().getFirst().get("id")).isNotEqualTo(page2.items().getFirst().get("id"));
        assertThat(service.list("patients","","","Female","","",1,100).items()).allMatch(r->r.get("gender").equals("Female"));
        assertThat(new String(service.export("patients","Amelia","","","",""),java.nio.charset.StandardCharsets.UTF_8)).contains("Amelia Carter").doesNotContain("Oliver Bennett");
        assertThatThrownBy(()->service.delete("patients","PAT-0001","QA")).hasMessageContaining("linked");
    }
    @Test void opdCheckInCreatesOneQueueAndVisit(){
        var source=new LinkedHashMap<>(service.get("opd","OPD-0001"));source.put("name","QA check-in");source.remove("id");source.put("patient",service.save("patients",null,Map.of("name","QA OPD patient","gender","Other","dateOfBirth","1990-01-01","phone","00000000"),"QA").get("id"));
        var opd=service.save("opd",null,source,"QA");long queues=service.list("queues","","","","","",1,100).total(),visits=service.list("visits","","","","","",1,100).total();
        var updated=service.transition("opd",opd.get("id"),"Check in",Map.of(),"QA");assertThat(updated.get("queueNumber")).startsWith("Q-");
        assertThat(service.list("queues","","","","","",1,100).total()).isEqualTo(queues+1);assertThat(service.list("visits","","","","","",1,100).total()).isEqualTo(visits+1);
        assertThatThrownBy(()->service.transition("opd",opd.get("id"),"Check in",Map.of(),"QA")).hasMessageContaining("not available");
    }
    @Test void invoicesPaymentsAndRefundsStayConsistent(){
        var data=new LinkedHashMap<String,String>();data.put("name","QA invoice");data.put("patient","PAT-0001");data.put("date",LocalDate.now().toString());data.put("dueDate",LocalDate.now().plusDays(7).toString());data.put("amount","999");data.put("discount","10");data.put("tax","10");data.put("items","Consultation | 2 | 50");
        var invoice=service.save("billing",null,data,"QA");assertThat(invoice.get("amount")).isEqualTo("100.00");assertThat(invoice.get("total")).isEqualTo("99.00");service.transition("billing",invoice.get("id"),"Issue invoice",Map.of(),"QA");
        var payment=service.save("payments",null,Map.of("name","QA receipt","invoice",invoice.get("id"),"patient","PAT-0001","date",LocalDate.now().toString(),"amount","99","method","Cash"),"QA");
        assertThat(service.get("billing",invoice.get("id")).get("status")).isEqualTo("Paid");assertThat(service.get("billing",invoice.get("id")).get("balance")).isEqualTo("0.00");
        assertThatThrownBy(()->service.save("payments",null,Map.of("name","Overpay","invoice",invoice.get("id"),"patient","PAT-0001","date",LocalDate.now().toString(),"amount","1","method","Cash"),"QA")).isInstanceOf(ResponseStatusException.class);
        service.transition("payments",payment.get("id"),"Refund",Map.of(),"QA");assertThat(service.get("billing",invoice.get("id")).get("balance")).isEqualTo("99.00");
    }
    @Test void workflowRequiresSequenceAndSufficientStock(){
        var order=new LinkedHashMap<>(service.get("laboratory","LAB-0001"));var created=service.save("laboratory",null,order,"QA");
        assertThatThrownBy(()->service.transition("laboratory",created.get("id"),"Approve",Map.of(),"QA")).hasMessageContaining("not available");
        for(String action:List.of("Collect sample","Start test","Enter result","Review","Approve"))service.transition("laboratory",created.get("id"),action,Map.of(),"QA");
        assertThat(service.get("laboratory",created.get("id")).get("status")).isEqualTo("Approved");
        var rx=new LinkedHashMap<>(service.get("pharmacy","RX-0001"));rx.put("quantity","100000");var prescription=service.save("pharmacy",null,rx,"QA");service.transition("pharmacy",prescription.get("id"),"Review",Map.of(),"QA");
        assertThatThrownBy(()->service.transition("pharmacy",prescription.get("id"),"Dispense",Map.of(),"QA")).hasMessageContaining("Insufficient");
    }
    @Test void apiRequiresSessionAndCsrf()throws Exception {
        mvc.perform(get("/api/references")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/role").session(session()).contentType("application/json").content("{\"role\":\"Doctor\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/role").session(session()).with(csrf()).contentType("application/json").content("{\"role\":\"Doctor\"}")).andExpect(status().isOk());
    }
    @Test void filesRemainBoundedAndAuditIsReadOnly(){
        String id=service.attach("patients","PAT-0001","demo.txt","synthetic".getBytes(),"text/plain","QA");assertThat(service.attachment(id).get("name")).isEqualTo("demo.txt");
        assertThatThrownBy(()->service.attach("patients","PAT-0001","bad.html","html".getBytes(),"text/html","QA")).hasMessageContaining("Choose");
        assertThatThrownBy(()->service.delete("audit","LOG-0001","QA")).hasMessageContaining("immutable");
    }
    @Test void procurementReceiptAndDispensingUpdateStockOnce(){
        var order=new LinkedHashMap<>(service.get("procurement","PO-0001"));order.put("quantity","10");order.put("name","QA purchase order");
        var created=service.save("procurement",null,order,"QA");long before=service.list("stock","","","","","",1,100).total();
        for(String action:List.of("Submit","Approve","Place order","Receive goods"))service.transition("procurement",created.get("id"),action,Map.of(),"QA");
        assertThat(service.list("stock","","","","","",1,100).total()).isEqualTo(before+1);
        assertThatThrownBy(()->service.transition("procurement",created.get("id"),"Receive goods",Map.of(),"QA")).hasMessageContaining("not available");
        var rx=new LinkedHashMap<>(service.get("pharmacy","RX-0001"));rx.put("quantity","2");var prescription=service.save("pharmacy",null,rx,"QA");
        double quantityBefore=Double.parseDouble(service.get("stock","STK-0001").get("quantity"));
        service.transition("pharmacy",prescription.get("id"),"Review",Map.of(),"QA");service.transition("pharmacy",prescription.get("id"),"Dispense",Map.of(),"QA");
        assertThat(Double.parseDouble(service.get("stock","STK-0001").get("quantity"))).isEqualTo(quantityBefore-2);
        assertThatThrownBy(()->service.transition("pharmacy",prescription.get("id"),"Dispense",Map.of(),"QA")).hasMessageContaining("not available");
    }
    @Test void csvImportIsAtomicAndResolvesReferenceNames(){
        String header="name,generic,category,brand,unit,strength,price,manufacturer,notes\r\n";
        String good="\"QA medicine, one\",Demo ingredient,Analgesic,CarePlus Essentials,Tablet,500 mg,2.50,MedSource Manufacturing,\"quoted \"\"text\"\"\"\r\n";
        var rows=CsvReader.read(header+good);assertThat(rows.getFirst().get("name")).isEqualTo("QA medicine, one");
        long before=service.list("medicines","","","","","",1,100).total();
        assertThat(service.importMedicines(rows,"QA").get("created")).isEqualTo(1);
        assertThat(service.list("medicines","QA medicine, one","","","","",1,100).items().getFirst().get("categoryLabel")).isEqualTo("Analgesic");
        var invalid=new LinkedHashMap<>(rows.getFirst());invalid.put("category","Missing category");
        assertThatThrownBy(()->service.importMedicines(List.of(rows.getFirst(),invalid),"QA")).hasMessageContaining("Row 2");
        assertThat(service.list("medicines","","","","","",1,100).total()).isEqualTo(before+1);
        assertThatThrownBy(()->CsvReader.read(header+"\"unclosed")).hasMessageContaining("Unclosed");
    }
    @Test void ageAndDepartmentFiltersCompose(){
        assertThat(service.list("patients","","","Female;ageMin=30;ageMax=60","","",1,100).items()).allMatch(r->r.get("gender").equals("Female")&&java.time.Period.between(LocalDate.parse(r.get("dateOfBirth")),LocalDate.now()).getYears()>=30);
        var appointment=service.get("appointments","APT-0001");
        assertThat(service.list("appointments","","",appointment.get("department")+";doctor="+appointment.get("doctor"),"","",1,100).items()).allMatch(r->r.get("doctor").equals(appointment.get("doctor")));
        var lab=service.list("laboratory","","","",LocalDate.now().toString(),LocalDate.now().toString(),1,100);assertThat(lab.counts().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(lab.total());
    }
    @Test void noDatabaseIsLoaded(@Autowired org.springframework.context.ApplicationContext context){assertThat(context.getBeansOfType(javax.sql.DataSource.class)).isEmpty();}
    @Test void prescriptionDetailsLinkItsMedicineStock(){
        var prescription=service.get("pharmacy","RX-0001");
        @SuppressWarnings("unchecked") var related=(Map<String,List<Map<String,String>>>)service.details("pharmacy",prescription.get("id")).get("related");
        assertThat(related.get("stock")).isNotEmpty().allMatch(batch->batch.get("medicine").equals(prescription.get("medicine")));
    }
}

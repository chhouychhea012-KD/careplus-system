package com.example.e_hospital.management.prototype.service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** Small bounded RFC 4180 reader supporting quoted commas, newlines, and escaped quotes. */
public final class CsvReader {
    private CsvReader(){}
    public static List<Map<String,String>> read(String text){
        text=text.replaceFirst("^\\uFEFF","");List<List<String>> lines=new ArrayList<>();List<String> line=new ArrayList<>();StringBuilder value=new StringBuilder();boolean quoted=false;
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c=='\"'){if(quoted&&i+1<text.length()&&text.charAt(i+1)=='\"'){value.append('\"');i++;}else quoted=!quoted;}else if(c==','&&!quoted){line.add(value.toString().strip());value.setLength(0);}else if((c=='\n'||c=='\r')&&!quoted){line.add(value.toString().strip());value.setLength(0);if(line.stream().anyMatch(v->!v.isBlank()))lines.add(line);line=new ArrayList<>();if(c=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;if(lines.size()>201)bad("A CSV can contain at most 200 medicine records.");}else value.append(c);}
        if(quoted)bad("Unclosed quote in CSV.");if(!line.isEmpty()||!value.isEmpty()){line.add(value.toString().strip());lines.add(line);}if(lines.size()<2)bad("Add a header row and at least one medicine.");
        var headers=lines.removeFirst();if(new HashSet<>(headers).size()!=headers.size()||!headers.containsAll(List.of("name","generic","category","brand","unit","strength","price")))bad("CSV headers must include name, generic, category, brand, unit, strength, and price.");
        List<Map<String,String>> rows=new ArrayList<>();for(var values:lines){if(values.size()!=headers.size())bad("Every CSV row must have the same number of columns as the header.");Map<String,String> row=new LinkedHashMap<>();for(int i=0;i<headers.size();i++)row.put(headers.get(i),values.get(i));rows.add(row);}return rows;
    }
    private static void bad(String msg){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,msg);}
}

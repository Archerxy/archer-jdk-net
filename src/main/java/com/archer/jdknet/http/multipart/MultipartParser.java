package com.archer.jdknet.http.multipart;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import com.archer.jdknet.http.HttpException;
import com.archer.jdknet.http.HttpRequest;
import com.archer.jdknet.http.HttpStatus;


public class MultipartParser {

	private static final String MULTIPART = "multipart/form-data; boundary=";

    private static final char[] KEY_NAME = {'n', 'a', 'm', 'e', '=', '"'};
    private static final char[] FILE_NAME = {'f','i','l','e','n','a','m','e', '=', '"'};
    private static final char QT = '"';
    private static final char LF = '\n';
    
    private static final int SEP_START = 1;
    private static final int HEAD_START = 2;

    public static List<Multipart> parse(HttpRequest req) {
    	if(!req.getContentType().startsWith(MULTIPART)) {
    		throw new HttpException(HttpStatus.BAD_REQUEST.getCode(), 
    				"invalid mulipart formdata content-type:" + req.getContentType());
    	}
        String sep = "--"+req.getContentType().substring(MULTIPART.length()).trim();
        return parse(sep, req.getContent());
    }
    private static List<Multipart> parse(String sepStr, byte[] content) {
    	char[] sep = sepStr.toCharArray();
    	int off = 0, state = SEP_START, vs = 0, cs = -1, lastLF = 0;
		Multipart part = null;
		List<Multipart> partList = new LinkedList<>();
    	for(; off < content.length; off++) {
    		if(state == SEP_START && off + sep.length < content.length) {
        		boolean ok = true;
        		for(int i = 0; i < sep.length; i++) {
        			if(sep[i] != content[off + i]) {
        				ok = false;
        				break ;
        			}
        		}
        		if(ok) {
        			if(cs > 0 && part != null) {
        				part.setContent(Arrays.copyOfRange(content, cs, off - 2));
        				partList.add(part);
        			}
        			part = new Multipart();
        			state = HEAD_START;
        			off += sep.length;
        		}
    		}
    		if(state == HEAD_START) {
    			part.setType(MultipartType.TEXT);
    			while(off < content.length) {
    				if(content[off] == LF) { 
    					if(off - lastLF <= 2) {
        					state = SEP_START;
        					off++;
    					} else {
        					lastLF = off;	
    					}
    				}
        			if(state == SEP_START) {
        				cs = off;
        				break;
        			}
       				if(content[off] == KEY_NAME[0] && content[off+1] == KEY_NAME[1] && 
     				   content[off+2] == KEY_NAME[2] && content[off+3] == KEY_NAME[3] && 
     				   content[off+4] == KEY_NAME[4] && content[off+5] == KEY_NAME[5]) {
       					
     					off += KEY_NAME.length;
     					vs = off;

         				while(content[off] != QT) {
         					off++;
         					if(off >= content.length) {
         			    		throw new HttpException(HttpStatus.BAD_REQUEST.getCode(), 
         			    				"invalid mulipart formdata head:" + 
         			    				new String(Arrays.copyOfRange(content, vs, vs + KEY_NAME.length + 16)));
         					}
         				}
     					part.setName(new String(Arrays.copyOfRange(content, vs, off)));
     				}
       				
       				if(content[off] == FILE_NAME[0] && content[off+1] == FILE_NAME[1] && 
     				   content[off+2] == FILE_NAME[2] && content[off+3] == FILE_NAME[3] && 
     				   content[off+4] == FILE_NAME[4] && content[off+5] == FILE_NAME[5] && 
     				   content[off+6] == FILE_NAME[6] && content[off+7] == FILE_NAME[7] && 
     				   content[off+8] == FILE_NAME[8] && content[off+9] == FILE_NAME[9]) {
       					
     					off += FILE_NAME.length;
     					vs = off;
     					while(content[off] != QT) {
         					off++;
         					if(off >= content.length) {
         			    		throw new HttpException(HttpStatus.BAD_REQUEST.getCode(), 
         			    				"invalid mulipart formdata head:" + 
         			    				new String(Arrays.copyOfRange(content, vs, vs + FILE_NAME.length + 16)));
         					}
        				}
    					part.setType(MultipartType.FILE);
    					part.setFileName(new String(Arrays.copyOfRange(content, vs, off)));
     				}
        			off++;
    			}
    		}
    		
    	}
        return partList;
    }
}

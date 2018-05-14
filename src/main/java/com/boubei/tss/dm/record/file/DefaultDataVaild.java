package com.boubei.tss.dm.record.file;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.boubei.tss.EX;
import com.boubei.tss.dm.DMUtil;
import com.boubei.tss.dm.ddl._Database;
import com.boubei.tss.dm.ddl._Field;
import com.boubei.tss.dm.dml.SQLExcutor;
import com.boubei.tss.util.EasyUtils;

/**
 * TSS默认数据校验器
 */
public class DefaultDataVaild implements IDataVaild {

	public List<Integer> vaild(_Database _db, String[] rows, String[] headers, List<String> errLines, List<Integer> emptyLineIndexs) {
		
		List<Integer> errLineIndexs = new ArrayList<Integer>();
		Map<String, List<Object>> colValues = new HashMap<String, List<Object>>();
		
		// TODO 非空 && 带默认值或valSQL的字段 如果在导入文件里没有，则自动加上
		
		for(int index = 1; index < rows.length; index++) { // 第一行为表头，不要
			
			String row = rows[index];
			String[] fieldVals = (row+ " ").split(",");
			
			// 0、检查列数是否和表头列数相等
			if(fieldVals.length != headers.length) {
				String err;
				if(fieldVals.length > headers.length) {
					err = "行数据列数大于表头列数量";
				} else {
					err = EX.DM_23;
				}
				errLines.add( index + "," + err.replaceAll(",", "，") + "," + row );
				errLineIndexs.add(index);
				continue;
			}
			
			List<String> values = new ArrayList<String>();
			List<String> errors = new ArrayList<String>();
			Map<String, Object> valuesMap = new HashMap<String, Object>();
			
			for(int j = 0; j < fieldVals.length; j++) {
    			String value = fieldVals[j].trim();
    			values.add(value);
    			
    			String filedLabel = headers[j];
    			String fieldCode = _db.ncm.get(filedLabel);
    			
    			// 1、根据【默认值】补齐字段信息：关联字段值获取等
    			String valSQL = _db.csql.get(fieldCode);
    			if( EasyUtils.isNullOrEmpty(value) && !EasyUtils.isNullOrEmpty(valSQL) ) {
    				// 自动关联字段值（eg：根据客户 + 位置信息获取位置的编码值，条件列需要在被补列前面）
    	    		String _sql = DMUtil.fmParse(valSQL, valuesMap).replaceAll("\\^", "'");
    	    		List<Map<String, Object>> result = SQLExcutor.query(_db.datasource, _sql);
    	    		if( result.size() > 1 ) {
    	    			errors.add(filedLabel + "匹配到多个数据");
    	    		}
    	    		else if( result.size() == 0 ) {
    	    			errors.add(filedLabel + "没有匹配的数据");
    	    		} else {
    	    			value = EasyUtils.obj2String(result.get(0).get(fieldCode));
    	    		}
    			}
    			
    			// 1、nullable、unique、type 校验
    			String nullable = _db.cnull.get(fieldCode);
    			String unique 	= _db.cuni.get(fieldCode);
    			String type	= _db.ctype.get(fieldCode);
    			
    			if( type != null) {
	    			try {
	    				value = EasyUtils.obj2String(  DMUtil.preTreatValue(value, type) );
	    			} catch(Exception e) {
	    				errors.add(filedLabel + "数据类型【" +type+ "】异常:" + e.getMessage());
	    			}
    			}
    			
    			String defaultVal = _db.cval.get(fieldCode);
    			if("false".equals(nullable) && EasyUtils.isNullOrEmpty(value) && !_Field.isAutoSN(defaultVal)) {
    				errors.add(filedLabel + "值不能为空");
    			}
    			if("true".equals(unique) && !EasyUtils.isNullOrEmpty(value)) {
    				List<Object> colList = colValues.get(fieldCode);
    				if(colList == null) {
    					colValues.put(fieldCode, colList = new ArrayList<Object>());
    				}
    				int rowIndex = colList.indexOf(value);
					if( rowIndex >= 0) {
    					errors.add(filedLabel + "值不唯一，和第" +(rowIndex+1)+ "行数据重复");
    				} else {
    					colList.add(value);
    				}
    			}
    			
    			// 2、正则表达式校验
    			String checkReg = _db.creg.get(fieldCode);
    			if( !EasyUtils.isNullOrEmpty(checkReg) && !EasyUtils.isNullOrEmpty(value) ) {
    				String regExp = checkReg.replaceAll("\\\\","\\\\\\\\");  // JS 正则转换为 JAVA正则
        	        Pattern p = Pattern.compile(regExp);  
        	        if( !p.matcher(value).matches() ) {
        	        	String errorMsg = _db.cerr.get(fieldCode);
        	        	errors.add( (String) EasyUtils.checkNull(errorMsg, filedLabel + "值校验异常") );
        	        }
    			}
    			
    			valuesMap.put(fieldCode, value);
        	}
			
			// 判断是否每个字段都没有数据，是的话为空行
			if( EasyUtils.isNullOrEmpty( EasyUtils.list2Str(values, "") ) ) {
				emptyLineIndexs.add(index);
				continue;
			}
			
			if( errors.size() > 0 ) {
				String errLine = index + "," + EasyUtils.list2Str(errors, "|").replaceAll(",", "，") + "," + row;
				
				errLines.add(errLine);
				errLineIndexs.add(index);
				continue;
			}
			
			rows[index] = EasyUtils.list2Str(values);
			
		}
		
		return errLineIndexs ;
	}
	
}
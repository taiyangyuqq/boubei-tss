/* ==================================================================   
 * Created [2016-3-5] by Jon.King 
 * ==================================================================  
 * TSS 
 * ================================================================== 
 * mailTo:boubei@163.com
 * Copyright (c) boubei.com, 2015-2018 
 * ================================================================== 
 */

package com.boubei.tss.dm.record.file;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;

import com.boubei.tss.EX;
import com.boubei.tss.dm.DataExport;
import com.boubei.tss.dm.ddl._Database;
import com.boubei.tss.dm.record.Record;
import com.boubei.tss.dm.record.RecordService;
import com.boubei.tss.framework.Global;
import com.boubei.tss.framework.exception.BusinessException;
import com.boubei.tss.framework.web.servlet.AfterUpload;
import com.boubei.tss.modules.sn.SerialNOer;
import com.boubei.tss.util.EasyUtils;
import com.boubei.tss.util.FileHelper;

/**
 * 根据数据表提供的导入模板，填写后导入实现批量录入数据。
 * CSV文件需满足条件：
 * 1、模板从 xdata/import/tl/ 接口导出
 * 2、表头列的数量和顺序 和 录入表的字段信息严格一致
 * 3、每一行数据的列数及顺序 == 表头的列数及顺序，不能多也不能少
 * 4、每个字段值不允许存在 换行符、英文逗号
 * 
 * TODO 批量插入，如果某一批（目前10000一批）出错，如何回滚所有已经插入的数据
 */
public class ImportCSV implements AfterUpload {

	Logger log = Logger.getLogger(this.getClass());
	
	RecordService recordService = (RecordService) Global.getBean("RecordService");

	public String processUploadFile(HttpServletRequest request,
			String filepath, String oldfileName) throws Exception {

		Long recordId  = Long.parseLong(request.getParameter("recordId"));
		Record record = recordService.getRecord(recordId);
		_Database _db = _Database.getDB(record);

		// 解析附件数据
		File targetFile = new File(filepath);
		String dataStr = FileHelper.readFile(targetFile, DataExport.CSV_CHAR_SET); // "UTF-8"
		dataStr = dataStr.replaceAll(";", ","); // mac os 下excel另存为csv是用分号;分隔的
		String[] rows = EasyUtils.split(dataStr, "\n");
		
		List<String> snList = null;
		List<Map<String, String>> valuesMaps = new ArrayList<Map<String, String>>();
		String[] fields = rows[0].split(",");
		for(int index = 1; index < rows.length; index++) { // 第一行为表头，不要
			String row = rows[index];
			String[] fieldVals = (row+ " ").split(",");
			
			if(fieldVals.length != fields.length) {
				throw new BusinessException(EX.parse(EX.DM_23, index));
			}
			
			Map<String, String> valuesMap = new HashMap<String, String>();
			String sb = "";
			for(int j = 0; j < fieldVals.length; j++) {
    			String value = fieldVals[j].trim();
    			value = value.replaceAll("，", ","); // 导出时英文逗号替换成了中文逗号，导入时替换回来
    			sb += value;
    			
    			// 检查值为空的字段，是否配置自动取号规则，是的话先批量取出一串连号
    			String defaultVal = _db.fieldValues.get(j);
    			if( EasyUtils.isNullOrEmpty(value) && (defaultVal+"").endsWith("yyMMddxxxx")) {
    				String preCode = defaultVal.replaceAll("yyMMddxxxx", "");
    				if(snList == null) {
    					snList = new SerialNOer().create(preCode, rows.length);
    				}
    				value = snList.get(index - 1);
    			}
    			
				valuesMap.put(_db.fieldCodes.get(j), value);
        	}
			if( EasyUtils.isNullOrEmpty(sb) ) { // 判断是否每个字段都没有数据，是的话为空行
				continue;
			}
			
			valuesMaps.add(valuesMap);
			
			if(valuesMaps.size() == 10000) { // 按每一万批量插入一次
				_db.insertBatch(valuesMaps);
				valuesMaps.clear();
			}
		}
    	_db.insertBatch(valuesMaps);
		
		// 向前台返回成功信息
		return "parent.alert('导入成功！请刷新查看。'); parent.openActiveTreeNode();";
	}
	
}
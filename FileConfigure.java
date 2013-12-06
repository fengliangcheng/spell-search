package com.tcgroup.common.spell;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
/**
 * @description 通过读取文件完成对汉字拼音表和词典的初始化
 * @author fengliangcheng
 * @update 2013-7-3 下午4:55:13 
 */

public class FileConfigure implements ConfigureData{
	/**
     * @description 从文件读取汉字拼音配置表
     * @author fengliangcheng
     * @update 2013-7-3 下午5:07:26
     * @return 汉字及其读音的映射表
     */
	public Map<Character, String> generatePyConfigSet()
    {
    	//读取配置文件中的汉字拼音配置关系
    	String pyFilePath = "pinyin.txt";
        String record;
        HashMap<Character, String> initChinesePyMap = new HashMap<Character, String>();
        try
        {
            BufferedReader pyReader = new BufferedReader(new FileReader(pyFilePath));
            try
            {
                while((record = pyReader.readLine()) != null)
                {
                    String[] array = record.split("\t");
                    if(array.length >= 2)
                    {
                        char chinese = (array[0].charAt(0));
                        String pyStr = array[1];
                        initChinesePyMap.put(chinese, pyStr);
                    }
                }
            }
            finally
            {
                pyReader.close();
            }
        }
        catch(IOException e)
        {
            throw new RuntimeException(e);
        }
        return initChinesePyMap; 
    }
    
	 /**
     * @description 从文件读取各种词典
     * @author fengliangcheng
     * @update 2013-7-3 下午5:07:26
     * @return 词典集合
     */
    public Map<String, HashSet<String>> generateDictSets()
    {
    	//读取词典文件
        Map<String, HashSet<String>> dictGroups = new HashMap<String, HashSet<String>>();
        String dictName = "cha";
        HashSet<String> dict = read_dict("charge.txt");
        if(null != dict && !dict.isEmpty())
        {
        	dictGroups.put(dictName, dict);
        }
        
        return dictGroups;
    }
    
    /**
     * @description 从文件读取词典
     * @author fengliangcheng
     * @update 2013-7-3 下午5:07:26
     * @param dictFilePath 词典文件的绝对路径
     * @return 存放了词语的Set
     */
    private HashSet<String> read_dict(final String dictFilePath)
    {
    	HashSet<String> initDictWords = new HashSet<String>();
        String record;
        try
        {
            BufferedReader dictReader = new BufferedReader(new FileReader(dictFilePath));
            try
            {
                while((record = dictReader.readLine()) != null)
                {
                    initDictWords.add(record);
                }
            }
            finally
            {
                dictReader.close();
            }
        }
        catch(IOException e)
        {
            throw new RuntimeException(e);
        } 
        return initDictWords;
    }  
    
    
    /**
     * @description 测试方法
     * @author fengliangcheng
     * @update 2013-7-3 下午4:55:19
     * @param args
     * @throws IOException
     */
    public static void main(String[] args)throws IOException
    {
    	ConfigureData configure = new FileConfigure();
    	boolean enableShortSubMatched = true;
    	KBSmarter smarter = KBSmarter.getInstance(configure, enableShortSubMatched);
        long begin = System.currentTimeMillis();
        smarter.init();
        long end = System.currentTimeMillis();
        System.out.println("initing consumed:" + (end - begin) + " ms.");

        BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));

        //命令行测试匹配、词典reload过程
        String input;
        while(true)
        {
            input = stdIn.readLine(); 
            
            if(input.equals("exit"))
            {
                break; //终止测试过程
            }
            else if(input.startsWith("reload"))
            {
                String[] array = input.split(" ");
                if(array.length >= 3)
                {
                    String dictName = array[1];
                    HashSet<String> dict_set = new HashSet<String>();
                    String line;
                    try
                    {
                    	BufferedReader br = new BufferedReader(new FileReader(array[2]));
                    	try
                    	{
                    		while((line = br.readLine()) != null)
                    		{
                    			dict_set.add(line);
                    		}
                    	}
                        finally
                        {
                        	br.close();
                        }
                    }
                    catch(IOException e)
                    {
                    	throw new RuntimeException(e);
                    }
                    smarter.dictionary_reLoad(dictName, dict_set);
                }
            }
            else
            {
                String[] array = input.split(" ");
                if(array.length >= 2)
                {
	                String dictName = array[0]; //第一个参数是词典名称
	                String prefix = array[1]; //第二个参数是匹配的前缀
	                begin = System.currentTimeMillis();
	                ArrayList<String> resultList = smarter.findMatch(dictName, prefix, 20);
	                System.out.println("matching words as following:");
	                for(String py: resultList) //打印匹配结果
	                {
	                    System.out.println(py);
	                }
	                end = System.currentTimeMillis();
	                System.out.println("matching process consumed " + (end - begin) + "ms.");
                }
            }
        }
    }
}

package com.tcgroup.common.spell;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @description 键盘精灵功能的主类,支持多词典
 * @author fengliangcheng
 * @update 2013-7-3 上午9:48:42 
 */

public class KBSmarter {
    private Map<Character, List<String>> mapPyTable_; //存储汉字-->拼音关系
    private Map<String, HashSet<String>> mapDictWords_ ; //存储词典中的所有词
    private Map<String, AtomicReference<Trie>> mapAtomicRefer_; //切换前缀匹配trie树的原子操作对象
    private Map<String, AtomicReference<Trie>> mapSubAtomicRefer_; //切换子串匹配trie树的原子操作对象
    private ConfigureData configureData_; //该对象用于获取汉字拼音配置表和词典
    private boolean enableShortSubMatched_; //是否需要支持简拼子串匹配
    private static KBSmarter smarter_; //采用单件模式，实现资源共享

    /**
     * constructors of the class
     */
    private KBSmarter(ConfigureData data, boolean enableShortSubMatched)
    {
    	configureData_ = data;
    	enableShortSubMatched_ = enableShortSubMatched;
    	mapAtomicRefer_ = new HashMap<String, AtomicReference<Trie>>();
        mapSubAtomicRefer_ = new HashMap<String, AtomicReference<Trie>>();
    } 
    
    /**
     * @description 获取单个实例对象,注意调用本方法需要在并发操作之前
     * @author fengliangcheng
     * @update 2013-7-3 下午7:50:37
     * @data 实现了ConfigureData接口的类对象
     * @enableShortSubMatched 是否支持简拼子串匹配
     * @return 单个实例对象，用于前缀匹配
     */
    public static KBSmarter getInstance(ConfigureData data, boolean enableShortSubMatched)
    {
    	if(null == smarter_)
    	{
	    	smarter_ = new KBSmarter(data, enableShortSubMatched);
	    	smarter_.init();
    	}
    	return smarter_;
    }

    /**
     * @description 初始化的模板方法,用户必须实现两个抽象方法
     * @author fengliangcheng
     * @update 2013-7-3 下午5:26:28
     * @return 0-失败 1-成功
     */
    public int init()
    {
    	Map<Character, String> pyTable = configureData_.generatePyConfigSet();
    	if(0 == init_pinyin(pyTable))
    	{
    		return 0;
    	}
    	
    	mapDictWords_ = configureData_.generateDictSets();
    	Iterator<Map.Entry<String, HashSet<String>>> iter = mapDictWords_.entrySet().iterator();
    	while(iter.hasNext())
    	{
    		Map.Entry<String, HashSet<String>> entry = iter.next();
    		String dictName = entry.getKey(); //词典名称
    		HashSet<String> dictSet = entry.getValue(); //存储词典词语的HashSet
    		if(0 == init_dict(dictName, dictSet))
    		{
    			return 0;
    		}
    	}
    	
    	return 1;
    }
      
    /**
     * @description 将词典加载到内存并生成trie树
     * @author fengliangcheng
     * @update 2013-7-3 上午10:15:21
     * @param dictName 词典简称
     * @param initDictWords 词典
     * @return 0-失败 1-成功
     */
    private int init_dict(final String dictName, final HashSet<String> initDictWords)
    {   
        Map<String, HashSet<String>> dictPyChineseTable = new HashMap<String, HashSet<String>>();//存储该词典全拼和简拼读音与汉字映射关系
        Map<String, HashSet<String>> dictSubPyChineseTable = null; //存储该词典简拼子串与汉字映射关系
        if(enableShortSubMatched_)
        {
        	dictSubPyChineseTable = new HashMap<String, HashSet<String>>();    
        }
        dictionary_load(initDictWords, dictPyChineseTable, dictSubPyChineseTable); //将词典文件中每个词都生成对应的拼音

        //将词典转化出来的全拼和简拼添加到前缀匹配trie树
        Trie prefixTrie = new Trie(mapPyTable_, dictPyChineseTable);
        Iterator<Map.Entry<String, HashSet<String>>> iter = dictPyChineseTable.entrySet().iterator();
        while(iter.hasNext())
        {
            Map.Entry<String, HashSet<String>> entry = iter.next();
            String py = entry.getKey(); //词语的拼音
            prefixTrie.insertWord(py); 
        }
        
        //为前缀匹配trie树建立一个原子操作对象,支持切换
        AtomicReference<Trie> ref = new AtomicReference<Trie>(prefixTrie);
        mapAtomicRefer_.put(dictName, ref);

        //将词典转化出来的简拼子串添加到子串匹配trie树
        if(enableShortSubMatched_)
        {
	        Trie subTrie = new Trie(mapPyTable_, dictSubPyChineseTable);
	        iter = dictSubPyChineseTable.entrySet().iterator();
	        while(iter.hasNext())
	        {
	            Map.Entry<String, HashSet<String>> entry = iter.next();
	            String py = entry.getKey();
	            subTrie.insertWord(py);
	        }
	        
	        //为子串匹配trie树建立一个原子操作对象,支持切换
	        AtomicReference<Trie> subRef = new AtomicReference<Trie>(subTrie);
	        mapSubAtomicRefer_.put(dictName, subRef);
        }

        return 1;
    }
    
    /**
     * @description 更新已有的词典或者加载新的词典
     * @author fengliangcheng
     * @update 2013-7-3 上午10:51:08
     * @param dictName 词典名称
     * @param dict_set 词典包含的词语
     * @return 0-失败 1-成功
     */
    public int dictionary_reLoad(final String dictName, HashSet<String> dict_set)
    {
        HashSet<String> exitingDict = mapDictWords_.get(dictName); //获取旧的词典
        if(null != exitingDict && exitingDict.size() == dict_set.size() && exitingDict.containsAll(dict_set))
        {
            System.out.println("dict hasn't changed!");
            return 0; //新的词典与旧词典完全一致，无需reload
        }
        else if(0 == dict_set.size())
        {
            System.out.println("new dict is empty!");
            return 0;
        }
        else
        {
            Map<String, HashSet<String>> newPyChineseTable = new HashMap<String, HashSet<String>>();
            Map<String, HashSet<String>> newSubPyChineseTable = null;//将词典文件中每个词都生成对应的拼音
            if(enableShortSubMatched_)
            {
            	newSubPyChineseTable = new HashMap<String, HashSet<String>>();
            }
            dictionary_load( dict_set, newPyChineseTable, newSubPyChineseTable);
            if(null != exitingDict)
            {
                exitingDict.clear();
            }
            mapDictWords_.put(dictName, dict_set);

            //将词典转化出来的全拼和简拼添加到前缀匹配trie树
            Trie newTrie = new Trie(mapPyTable_, newPyChineseTable);
            Iterator<Map.Entry<String, HashSet<String>>> iter = newPyChineseTable.entrySet().iterator();
            while(iter.hasNext())
            {
                Map.Entry<String, HashSet<String>> entry = (Map.Entry<String, HashSet<String>>)iter.next();
                String py = entry.getKey();
                newTrie.insertWord(py);
            }

            //将词典转化出来的简拼子串添加到trie树
            Trie newSubTrie = null;
            if(enableShortSubMatched_)
            {
	            newSubTrie = new Trie(mapPyTable_, newSubPyChineseTable);
	            iter = newSubPyChineseTable.entrySet().iterator();
	            while(iter.hasNext())
	            {
	                Map.Entry<String, HashSet<String>> entry = iter.next();
	                String py = entry.getKey();
	                newSubTrie.insertWord(py);
	            }
            }

            //原子切换前缀匹配trie树
            AtomicReference<Trie> atomicRefer = mapAtomicRefer_.get(dictName);
            if(null == atomicRefer)
            {
                atomicRefer = new AtomicReference<Trie>(newTrie);
                mapAtomicRefer_.put(dictName, atomicRefer);
            }
            else
            {
                atomicRefer.getAndSet(newTrie);
            }

            //原子切换子串匹配trie树
            if(enableShortSubMatched_)
            {
	            AtomicReference<Trie> atomicSubRefer = mapSubAtomicRefer_.get(dictName);
	            if(null == atomicSubRefer)
	            {
	                atomicSubRefer =  new AtomicReference<Trie>(newSubTrie);
	                mapSubAtomicRefer_.put(dictName, atomicSubRefer);
	            }
	            else
	            {
	                atomicSubRefer.getAndSet(newSubTrie);
	            }
            }
            return 1;
        }
    }   
    /**
     * @description 将汉字-->拼音映射关系中的拼音串按照逗号分割后,存储为HashMap<Character, ArrayList<String>>
     * @author fengliangcheng
     * @update 2013-7-3 上午10:05:08
     * @param pyTable 存储汉字-->拼音映射关系,例如:"乐\tyue,le"
     * @return 0-失败 1-成功
     */
    private int init_pinyin(final Map<Character, String> pyTable)
    {
    	int ret = 1;
    	mapPyTable_ = new HashMap<Character, List<String>>();
        Iterator<Map.Entry<Character, String>> iter = pyTable.entrySet().iterator();
        while(iter.hasNext())
        {
            Map.Entry<Character, String> entry = iter.next();
            Character chineseWord = entry.getKey();
            String pinYinStr = entry.getValue();
            String[] pyArray = pinYinStr.split(","); //汉字经常会有多个读音

            if(0 == pyArray.length)
            {
                continue;
            }

            List<String> tmpList = mapPyTable_.get(chineseWord);
            if(null == tmpList)
            {
                mapPyTable_.put(chineseWord, Arrays.asList(pyArray));
            }
        }
        
        if(0 == mapPyTable_.size())
        {
        	ret = 0;
        }
        return ret;
    }

    /**
     * @description 对常见字符进行全角到半角的转换
     * @author fengliangcheng
     * @update 2013-7-2 下午8:45:49
     * @param rawCh 原始字符
     * @return 返回对应的半角字符
     */
    private static final char fullToHalf(final char rawCh)
    {
        switch(rawCh)
        {
            case '（': return '(';
            case '）': return ')';
            case '、': return '.';
            case '，': return ',';
            case '。': return '.';
            default: return rawCh;
        }
    }
    
    /**
     * @description 将汉字词转化为全屏和简拼
     * @author fengliangcheng
     * @update 2013-7-3 上午10:12:40
     * @param set_words 输入词典
     * @param pyChineseTable 存储全拼和简拼与汉字词对应关系
     * @param pySubChineseTable 存储简拼子串与汉字词对应关系
     */
    private void dictionary_load(Set<String> set_words,
    		                            Map<String, HashSet<String>> pyChineseTable,
                                        Map<String, HashSet<String>> pySubChineseTable)
    {
        Vector<StringBuilder> pyVec = new Vector<StringBuilder>(); //保存一个词的所有全拼读音（汉字常有多个读音）
        Vector<StringBuilder> tmpPyVec = new Vector<StringBuilder>();
        Vector<StringBuilder> pyShortVec = new Vector<StringBuilder>(); //保存一个词的所有简拼读音（汉字常有多个读音）
        Vector<StringBuilder> tmpShortVec = new Vector<StringBuilder>();
        for(String word: set_words) //遍历每个词
        {
            for(int i = 0; i < word.length(); i++)//遍历该词的每个字
            {
                char rawCh = word.charAt(i);
                char ch = fullToHalf(rawCh); //全角转化为半角
                List<String> pyList = mapPyTable_.get((Character)ch);
                if(null == pyList || 0 == pyList.size()) //该字不是汉字
                {
                    if(ch >= 'A' && ch <= 'Z')
                    {
                        ch = (char)(ch + 32);
                    }

                    if(0 == pyShortVec.size())
                    {
                        StringBuilder sb = new StringBuilder();
                        sb.append(ch);
                        pyVec.add(sb); //全拼字符串

                        StringBuilder ssb = new StringBuilder();
                        ssb.append(ch);
                        pyShortVec.add(ssb); //简拼字符串
                    }
                    else
                    {
                        for(int k = 0; k < pyVec.size(); k++)
                        {
                            StringBuilder strBuilder = pyVec.get(k);
                            strBuilder.append(ch);
                        }

                        for(int k = 0; k < pyShortVec.size(); k++)
                        {
                            StringBuilder strBuilder = pyShortVec.get(k);
                            strBuilder.append(ch);
                        }
                    }
                }
                else //该字是汉字
                {
                    if(0 == pyShortVec.size())
                    {
                        for(String pinYin: pyList) //多个读音都要存储，支持多音字匹配
                        {
                            pyVec.add(new StringBuilder(pinYin));

                            char firstCh = pinYin.charAt(0);
                            StringBuilder sb = new StringBuilder();
                            sb.append(firstCh);
                            pyShortVec.add(sb);
                        }
                    }
                    else
                    {
                        for(int index = 0; index < pyList.size(); index++) //遍历多个读音
                        {
                            String pinYin = pyList.get(index);
                            for(StringBuilder sb: pyVec)
                            {
                                if(pyList.size() > (index + 1)) //前面pyList.size()-1个读音，都必须得生成新的StringBuilder对象
                                {//因为多个读音，导致数组的元素个数必须增加
                                    StringBuilder nsb = new StringBuilder(sb);
                                    nsb.append(pinYin);
                                    tmpPyVec.add(nsb);
                                }
                                else //对于最后一个读音，重复利用之前的StringBuilder对象
                                {
                                    sb.append(pinYin);
                                    tmpPyVec.add(sb);
                                }
                            }

                            for(StringBuilder tsb: pyShortVec)
                            {
                                if(pyList.size() > (index + 1)) //前面pyList.size()-1个读音，都必须得生成新的StringBuilder对象
                                {
                                    StringBuilder nsb = new StringBuilder(tsb);
                                    nsb.append(pinYin.charAt(0));
                                    tmpShortVec.add(nsb);
                                }
                                else //对于最后一个读音，重复利用之前的StringBuilder对象
                                {
                                    tsb.append(pinYin.charAt(0));
                                    tmpShortVec.add(tsb);
                                }
                            }
                        }
                        pyVec.clear();
                        for(StringBuilder sb: tmpPyVec)
                        {
                            pyVec.add(sb);
                        }
                        tmpPyVec.clear();

                        pyShortVec.clear();
                        for(StringBuilder sb: tmpShortVec)
                        {
                            pyShortVec.add(sb);
                        }
                        tmpShortVec.clear();
                    }
                }
            }

            for(StringBuilder sb: pyVec)//将全拼读音,添加到拼音--词语映射关系表
            {
                String str = sb.toString();
                HashSet<String> chineseWordsSet = pyChineseTable.get(str);
                if(null == chineseWordsSet)
                {
                    chineseWordsSet = new HashSet<String>();
                    pyChineseTable.put(str, chineseWordsSet);
                }
                chineseWordsSet.add(word);
            }

            for(StringBuilder sb: pyShortVec)//简拼读音,添加到拼音--词语映射关系表
            {
                String str = sb.toString();
                HashSet<String> chineseWordsSet = pyChineseTable.get(str);
                if(null == chineseWordsSet)
                {
                    chineseWordsSet = new HashSet<String>();
                    pyChineseTable.put(str, chineseWordsSet);
                }
                chineseWordsSet.add(word);

                if(enableShortSubMatched_)
                {
	                for(int i = 1; i < str.length(); i++) //获取所有简拼子串,添加到简拼子串--词语映射关系表
	                {
	                    String subShortPy = str.substring(i);
	                    HashSet<String> wordsSet = pySubChineseTable.get(subShortPy);
	                    if(null == wordsSet)
	                    {
	                        wordsSet = new HashSet<String>();
	                        pySubChineseTable.put(subShortPy, wordsSet);
	                    }
	                    wordsSet.add(word);
	                }
                }
            }
            pyVec.clear(); //在下一次循环迭代前清空
            pyShortVec.clear(); //在下一次循环迭代前清空
        }
    }    
    /**
     * @description 找出前缀串在词典中匹配的所有词
     * @author fengliangcheng
     * @update 2013-7-3 上午10:48:26
     * @param dictName 词典名称
     * @param prefix  需要匹配的前缀
     * @param maxCount 匹配结果的最多条数
     * @return 匹配结果的集合
     */
    public ArrayList<String> findMatch(final String dictName, final String prefix, int maxCount)
    {
        maxCount = (maxCount <= 0)?10:maxCount;
        maxCount = (maxCount > 30)?30:maxCount;

        HashSet<String> prefixMatchResults = new HashSet<String>();
        HashSet<String> subMatchResults = new HashSet<String>();
        ArrayList<String> matchResults = new ArrayList<String>();

        //优先进行前缀匹配
        AtomicReference<Trie> ref = mapAtomicRefer_.get(dictName);
        if(null != ref)
        {
            (ref.get()).findPrefix(prefix, maxCount, prefixMatchResults); //read_
        }
        matchResults.addAll(prefixMatchResults);

        //如果输入是字母数字且启用了简拼子串匹配功能,则需要进行简拼子串匹配
        if(matchResults.size() < maxCount && enableShortSubMatched_)
        {
            AtomicReference<Trie> subRef = mapSubAtomicRefer_.get(dictName);
            if(null != subRef)
            {
                (subRef.get()).findSubPrefix(prefix, maxCount - matchResults.size(), subMatchResults);
                subMatchResults.removeAll(prefixMatchResults);
                matchResults.addAll(subMatchResults);
            }
        }

        return matchResults;
    }
}

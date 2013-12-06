package com.tcgroup.common.spell;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/**
 * @description 用于前缀匹配的trie树
 * @author fengliangcheng
 * @update 2013-7-2 下午8:29:18 
 */

class Trie {
	
    /**
     * @description trie树的节点类
     * @author fengliangcheng
     * @update 2013-7-2 下午8:08:21 
     */
    
    static class CNode
    {
        HashMap<Character, CNode> childMap; //保存子节点的位置
        boolean isFinishState; //当前节点是否一个词的结束位置
        public CNode()
        {
            childMap = new HashMap<Character, CNode>();
            isFinishState = false;
        }
    }

    private CNode rootNode; //trie树根节点
    private Map<Character, List<String>> pinyinTable; //存储汉字->读音的映射表
    private Map<String, HashSet<String>> pinyinChineseTable; //存储拼音串->词语的映射表
    private static Character[] commonAlphabet; //存储26个常用字母的Charactor对象，用于插入拼音串时共享，减少对象的生成
    private static Character[] commonDigit; //存储10个常用数字的Charactor对象，用于插入拼音串时共享，减少对象的生成
    static
    {
        commonAlphabet = new Character['z' - 'a' + 1];
        for(int i = 0; i < 26; i++)
        {
            char ch = (char)('a' + i);
            commonAlphabet[i] = new Character(ch);
        }

        commonDigit = new Character[10];
        for(int i = 0; i < 10; i++)
        {
            char ch = (char)('0' + i);
            commonDigit[i] = new Character(ch);
        }
    }
  
    /**
     * constructors of the class
     * @param pyTable 存储汉字->读音的映射表
     * @param pcTable 存储拼音串->词语的映射表
     */
    public Trie(Map<Character, List<String>> pyTable, Map<String, HashSet<String>> pcTable)
    {
        rootNode = new CNode();
        pinyinTable = pyTable;
        pinyinChineseTable = pcTable;
    }
    

    /**
     * @description 插入字符串到trie树的外部接口
     * @author fengliangcheng
     * @update 2013-7-2 下午8:15:09
     * @param word 需要插入到trie树的字符串
     */
    public void insertWord(final String word)
    {
        if(word.length() > 0)
        {
            insert(rootNode, word, 0);
        }
    }

    /**
     * @description 前缀匹配的外部调用接口
     * @author fengliangcheng
     * @update 2013-7-2 下午8:14:00
     * @param prefix 前缀字符串
     * @param count 匹配结果最大条数
     * @param set_result 匹配结果保存的地方
     */
    public void findPrefix(final String prefix, final int count, Set<String> set_result)  
    {
        int ascii_count = 0; //前缀中包含的ascii字符个数
        for(int i = 0; i < prefix.length(); i++)
        {
            char ch = prefix.charAt(i);
            if(ch > 0 && ch < 128)
            {
                ascii_count++;
            }
        }
        boolean bFullAscii = (prefix.length() == ascii_count); //该前缀的所有字符都是ascii吗?若是，则无需对匹配结果进行校验
        boolean bFullChinese = (0 == ascii_count); //该前缀的所有字符都是非ascii吗？若是，则只需要简单地比较输入前缀是否为匹配结果的子串

        String prefixPy = convertSentenceToPy(prefix); //将前缀转化为拼音串
        CNode curr = rootNode; //从根节点开始，查看输入前缀是否能够匹配到trie树上
        int index = 0; //记录前缀的拼音字符串匹配的位置

        while(curr != null && index < prefixPy.length())
        {
            char first = prefixPy.charAt(index);
            HashMap<Character, CNode> tempMap = curr.childMap;
            boolean hasKey = tempMap.containsKey(first);
            if(hasKey)
            {
                curr = tempMap.get(first);
                index++; //继续匹配前缀拼音串的下一个字符
            }
            else
            {
                return; //trie树中无法匹配该前缀的拼音串
            }
        }

        if(null != curr && index == prefixPy.length()) //该前缀可以在trie树中进行匹配
        {
            if(curr.isFinishState) //当前已经匹配到一个词的结束位置
            {
                HashSet<String> chineseWordList = pinyinChineseTable.get(prefixPy); //获取该匹配拼音串的对应词语集合
                for(String word: chineseWordList)
                {
                    if(bFullAscii) //输入前缀都是ASCII字符，无需校验
                    {
                        set_result.add(word);
                    }
                    else if(bFullChinese)
                    {
                        if(filter(prefix, word))//输入前缀都是非ASCII字符，只需要判断输入前缀是当前匹配结果的子串
                        {
                            set_result.add(word);
                        }
                    }
                    else //输入的前缀既有拼音，又有汉字，需要对匹配到的词语和输入前缀进行一一校验
                    {
                        if(verifyWithPrefix(prefix, word))
                        {
                            set_result.add(word);
                        }
                    }
                }
            }
            findPrefix(curr, count, prefix, prefixPy, bFullAscii, bFullChinese, set_result); //继续遍历该节点的所有子树
        }
    }

    /**
     * @description 进行简拼子串匹配的外部调用接口，比如：通策医疗，其简拼为tcyl，用cyl或者yl进行匹配，就是简拼子串匹配
     * @author fengliangcheng
     * @update 2013-7-2 下午8:16:11
     * @param prefix 子串的前缀（所有字符一定是拼音或者数字）
     * @param count 匹配结果最大条数
     * @param set_result 匹配结果保存的地方
     */
    public void findSubPrefix(final String prefix, final int count, Set<String> set_result)
    {
        CNode curr = rootNode;
        int index = 0;//记录简拼子串（都是ASCII字符）前缀的匹配的位置

        while(curr != null && index < prefix.length())
        {
            char first = prefix.charAt(index);
            HashMap<Character, CNode> tempMap = curr.childMap;
            boolean hasKey = tempMap.containsKey(first);
            if(hasKey)
            {
                curr = tempMap.get(first);
                index++;
            }
            else
            {
                return; //该子串前缀不能匹配到某个简拼子串
            }
        }

        if(null != curr && index == prefix.length())
        {
            if(curr.isFinishState) //遍历到一个结束位置
            {
                HashSet<String> chineseWordList = pinyinChineseTable.get(prefix);
                for(String word: chineseWordList)
                {
                    if(set_result.size() >= count)
                    {
                        return;
                    }
                    set_result.add(word);
                }
            }
            findSubPrefix(curr, count, prefix, set_result);
        }
    }

    /**
     * @description 递归地将字符串插入到trie树上
     * @author fengliangcheng
     * @update 2013-7-2 下午8:29:24
     * @param curr trie树上当前遍历节点
     * @param word 需要插入到trie树的字符串
     * @param currIndex 字符串中当前在扫描的位置
     * @return 返回值暂时无用
     */
    private CNode insert(CNode curr, String word, int currIndex)
    {
        if(currIndex == word.length()) //字符串已经在trie树中插入完毕
        {
            curr.isFinishState = true; //当前递归到的节点标记为一个结束位置
            return curr;
        }

        char first = word.charAt(currIndex);
        Character firtCharer; //HashMap的key必须为对象类型，因此把char转化为Charactor
        if(first >= 'a' && first <= 'z') //是字母
        {
            firtCharer = commonAlphabet[first - 'a']; //使用共享的Charactor对象，减少内存消耗
        }
        else if(first >= '0' && first <= '9') //是数字
        {
            firtCharer = commonDigit[first - '0']; //使用共享的Charactor对象，减少内存消耗
        }
        else
        {
            firtCharer = new Character(first);
        }
        HashMap<Character, CNode> tempMap = curr.childMap;
        boolean hasChar = tempMap.containsKey(firtCharer);
        if(hasChar)//当前节点已经有该字符作为后缀
        {
            CNode node = tempMap.get(firtCharer);
            return insert(node, word, ++currIndex); //递归扫描后续字符，使整个字符串都插入到trie树
        }
        else//当前节点还没有出现该字符作为后缀
        {
            CNode tmpCNode = new CNode();
            tempMap.put(firtCharer, tmpCNode);
            return insert(tmpCNode, word, ++currIndex);//递归扫描后续字符，使整个字符串都插入到trie树
        }

    }
    /**
     * @description 执行递归前缀匹配操作
     * @author fengliangcheng
     * @update 2013-7-2 下午8:37:46
     * @param curr 当前匹配的trie树节点
     * @param count 最大返回结果条数
     * @param prefix 输入的前缀字符串，用于对匹配词语的校验
     * @param prefixPy 输入前缀字符串的拼音串，用于匹配查找
     * @param bFullAscii 输入的前缀字符串，是否全为ASCII字符
     * @param bFullChinese 输入的前缀字符串，是否全为非ASCII字符
     * @param set_result 保存匹配结果的地方
     */
    private void findPrefix(CNode curr, final int count,
                            final String prefix, String prefixPy,
                            final boolean bFullAscii, final boolean bFullChinese,
                            Set<String> set_result)
    {
        if(null == curr || set_result.size() >= count) //匹配条数已经满足要求
        {
            return;
        }

        StringBuilder tmpPrefixPy = new StringBuilder(); //循环体中需要
        tmpPrefixPy.append(prefixPy);
        HashMap<Character, CNode> tmap = curr.childMap;
        Iterator<Map.Entry<Character, CNode>> iter = tmap.entrySet().iterator();
        while(iter.hasNext() && set_result.size() < count) //遍历当前节点的所有后继节点
        {
            Map.Entry<Character, CNode> entry = iter.next();
            Character key = entry.getKey(); //后继节点对应的字符
            char ch = (char)(key);
            if(tmpPrefixPy.length() > prefixPy.length())
            {
                tmpPrefixPy.delete(prefixPy.length(), prefixPy.length() + 1); //删除上一个子节点插入的字符
            }
            tmpPrefixPy.append(ch);

            CNode node = entry.getValue(); 
            String strPrefixPy = tmpPrefixPy.toString();
            if(node.isFinishState) //匹配到一个词
            {
                Set<String> wordList = pinyinChineseTable.get(strPrefixPy);
                for(String word: wordList)
                {
                    if(set_result.size() >= count) //结果条数已经达到
                    {
                        return;
                    }
                    if(bFullAscii) //输入前缀没有汉字，无需校验
                    {
                        set_result.add(word);
                    }
                    else if(bFullChinese) //输入前缀都是汉字，检查匹配词语是否以它为前缀即可
                    {
                        if(filter(prefix, word))
                        {
                            set_result.add(word);
                        }
                    }
                    else //一个一个字符进行校验
                    {
                        if(verifyWithPrefix(prefix, word)) //校验通过
                        {
                            set_result.add(word);
                        }
                    }
                }
            }
            findPrefix(node, count, prefix, strPrefixPy, bFullAscii, bFullChinese, set_result);
        }
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
     * @description 将执行前缀匹配的字符串转化为拼音串（trie树上插入的都是拼音串）
     * @author fengliangcheng
     * @update 2013-7-2 下午8:46:51
     * @param word 执行前缀匹配的字符串
     * @return 对应的拼音字符串
     */
    private String convertSentenceToPy(final String word)
    {
        StringBuilder strBuilder = new StringBuilder();
        for(int i = 0; i < word.length(); i++)
        {
            char ch = fullToHalf(word.charAt(i));
            List<String> pyList = pinyinTable.get((Character)ch);
            if(null == pyList)
            {
                if(ch >= 'A' && ch <= 'Z') //大写转化为小写
                {
                    ch += 32;
                }
                strBuilder.append(ch);
            }
            else
            {
                String pinYin = pyList.get(0);
                strBuilder.append(pinYin);
            }
        }
        return strBuilder.toString();
    }
    
    /**
     * @description 将输入前缀串按照连续拼音和汉字分割开，比如：pufa银hang分割成:pufa 银 hang三个元素
     * @author fengliangcheng
     * @update 2013-7-2 下午8:49:34
     * @param word 输入前缀
     * @param vec_result 保存分割结果
     */
    private void separateEngChi(final String word, Vector<String> vec_result)
    {
        StringBuilder tmpPy = new StringBuilder();

        for(int i = 0; i < word.length(); i++)
        {
            char ch = fullToHalf(word.charAt(i));
            List<String> pyList = pinyinTable.get((Character)ch);
            if(null == pyList) //当前也是一个ASCII字符
            {
                tmpPy.append(ch);
            }
            else
            {
                if(tmpPy.length() > 0) //连续的ASCII字符
                {
                    vec_result.add(tmpPy.toString());
                    tmpPy.delete(0, tmpPy.length());
                }
                vec_result.add(word.substring(i, i + 1)); //一个汉字
            }
        }

        if(tmpPy.length() > 0)
        {
            vec_result.add(tmpPy.toString());
        }
    }
    
    /**
     * @description 将匹配结果和输入的前缀进行校验，通过校验的匹配词语才有效
     * @author fengliangcheng
     * @update 2013-7-2 下午8:56:32
     * @param inputWord 输入的前缀，按照汉字和连续拼音分割后得到的数组，比如：pufa银hang分割成:pufa 银 hang三个元素
     * @param sampleWords 匹配到的某个词语
     * @return true-通过校验,false-校验失败
     */
    private boolean doVerify(Vector<String> inputWord, final String sampleWords)
    {
        int j = 0; //当前匹配到数组中第几个元素
        int start = 0;
        int len = sampleWords.length();
        for(int i = 0; i < len && j < inputWord.size() && start < (inputWord.elementAt(j)).length();)
        {
            String inputElement = inputWord.elementAt(j);
            if((inputWord.size() == (j + 1)) && (inputElement.charAt(0) > 0 && inputElement.charAt(0) < 128))
            {
                return true;
            }
            char sample_curr = sampleWords.charAt(i);
            char input_curr = inputElement.charAt(start);
            if(sample_curr > 0 && sample_curr < 128) //匹配词语当前校验位置的字符是ASCII字符
            {
                if(input_curr != sample_curr)
                {
                    return false;
                }
                else
                {
                    i++;
                    if(start < inputElement.length() - 1)
                    {
                        start++;
                    }
                    else
                    {
                        j++; //指向下一个元素，比如"银"
                        start = 0;
                    }
                }
            }
            else //匹配词语当前校验位置的字符是汉字
            {
                if(input_curr > 0 && input_curr < 128) //输入前缀当前校验位置的字符是拼音
                {
                    char currChineseWord = sampleWords.charAt(i);
                    List<String> pyList = pinyinTable.get((Character)currChineseWord);
                    int currInputLeftLen = inputElement.length() - start;
                    int index = 0;
                    for(; index < pyList.size(); index++)
                    {
                        String pinYin = pyList.get(index);
                        int pyLen = pinYin.length();
                        if(currInputLeftLen >= pyLen && (inputElement.substring(start, start + pyLen)).equals(pinYin))
                        {
                            i += 1;
                            if(inputElement.length() == (pyLen + start))
                            {
                                j++;
                                start = 0;
                            }
                            else
                            {
                                start += pyLen;
                            }
                            break;
                        }
                    }
                    if(pyList.size() == index)
                    {
                        return false;
                    }
                }
                else //输入前缀当前校验位置的字符是汉字
                {
                    int currChineseLen = inputElement.length();
                    String currSample = sampleWords.substring(i, i + currChineseLen);
                    if(currSample.equals(inputElement))
                    {
                        j++;
                        start = 0;
                        i += currChineseLen;
                    }
                    else
                    {
                        return false;
                    }
                }
            }
        }
        return true;
    }
    
    /**
     * @description 输入前缀是汉字和拼音（数字）混合，验证匹配词语是否和输入前缀保持一致
     * @author fengliangcheng
     * @update 2013-7-3 上午9:10:12
     * @param prefix 输入前缀
     * @param dest 在trie树匹配到的词语
     * @return true-通过校验 false-校验失败，匹配词语无效
     */
    private boolean verifyWithPrefix(final String prefix, final String dest)
    {
		String lowerCasePrefix = new String(prefix);
		String lowerCaseDest =  new String(dest);
		lowerCasePrefix = lowerCasePrefix.toLowerCase();
		lowerCaseDest = lowerCaseDest.toLowerCase();
        Vector<String> tmpVec = new Vector<String>();
        separateEngChi(lowerCasePrefix, tmpVec); //将输入前缀字符串中的汉字和连续拼音分割开
        return doVerify(tmpVec, lowerCaseDest); //真正执行校验的方法
    }
    
    /**
     * @description 输入前缀全部是汉字，验证匹配词是否以它为前缀
     * @author fengliangcheng
     * @update 2013-7-3 上午9:07:36
     * @param prefix 输入前缀（全部是汉字）
     * @param dest 在trie树上匹配到的词语
     * @return true-校验通过 false-校验不通过
     */
    private boolean filter(final String prefix, final String dest)
    {
        return dest.length() >= prefix.length() && dest.startsWith(prefix);
    }

    /**
     * @description 递归执行简拼子串匹配的方法（用简拼子串的前缀查找trie树上匹配的简拼子串）
     * @author fengliangcheng
     * @update 2013-7-3 上午9:39:15
     * @param curr 当前匹配到的节点
     * @param count 最大结果条数
     * @param prefix 输入的简拼子串前缀
     * @param set_result 保存结果的地方
     */
    private void findSubPrefix(CNode curr, final int count, final String prefix, Set<String> set_result)
    {
        if(null == curr || set_result.size() >= count)
        {
            return;
        }

        StringBuilder tmpPy = new StringBuilder();
        tmpPy.append(prefix);
        HashMap<Character, CNode> tmap = curr.childMap;
        Iterator<Map.Entry<Character, CNode>> iter = tmap.entrySet().iterator();
        while(iter.hasNext() && set_result.size() < count) //遍历当前节点的所有子节点
        {
            Map.Entry<Character, CNode> entry = iter.next();
            char ch = (char)(entry.getKey());
            if(tmpPy.length() > prefix.length())
            {
                tmpPy.delete(prefix.length(), prefix.length() + 1);
            }
            tmpPy.append(ch);

            CNode node = entry.getValue();
            String strPrefixPy = tmpPy.toString();
            if(node.isFinishState)
            {
                Set<String> wordList = pinyinChineseTable.get(strPrefixPy);
                for(String word: wordList) //输入的简拼子串前缀都是拼音或者数字，所以无需校验匹配到的词语
                {
                    if(set_result.size() >= count)
                    {
                        return;
                    }
                    set_result.add(word);
                }
            }
            findSubPrefix(node, count, strPrefixPy, set_result);
        }
    }
}


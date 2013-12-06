package com.tcgroup.common.spell;

import java.util.HashSet;
import java.util.Map;


/**
 * @description 获取相关配置数据和词典的接口
 * @author fengliangcheng
 * @update 2013-7-3 下午7:53:33 
 */

public interface ConfigureData {
	Map<Character, String> generatePyConfigSet(); //获取汉字读音映射表,子类要实现
    Map<String, HashSet<String>> generateDictSets(); //获取需要建立trie树的词典,子类要实现
}

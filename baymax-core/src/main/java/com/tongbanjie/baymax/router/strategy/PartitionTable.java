package com.tongbanjie.baymax.router.strategy;

import com.tongbanjie.baymax.exception.BayMaxException;
import com.tongbanjie.baymax.parser.model.CalculateUnit;
import com.tongbanjie.baymax.parser.model.ConditionUnit;
import com.tongbanjie.baymax.parser.model.ConditionUnitOperator;
import com.tongbanjie.baymax.router.model.TargetTableEntity;
import com.tongbanjie.baymax.utils.Pair;

import java.util.*;

/**
 * Created by sidawei on 16/3/20.
 */
public class PartitionTable extends PartitionTableMetaData{

    public List<TargetTableEntity> execute(CalculateUnit calculateUnit) {
        // and 相连的条件
        Set<ConditionUnit/*column value*/> conditionUnits = calculateUnit.getTablesAndConditions().get(getLogicTableName());

        if (conditionUnits == null || conditionUnits.size() == 0){
            return null;
        }

        // 检查conditon

        List<TargetTableEntity> targetList = new ArrayList<TargetTableEntity>(1);

        for (PartitionColumn column : columns){
            ConditionUnit matchingConditon = null;
            for(ConditionUnit condition : conditionUnits){
                if (column.getName().equals(condition.getColumn()) && logicTableName.equals(condition.getTable())){
                    if (matchingConditon != null){
                        throw new BayMaxException("有多个相同列的Condition : " + condition);
                    }else {
                        matchingConditon = condition;
                    }
                }
            }
            // 寻找到了分区列
            if (matchingConditon != null){
                if (matchingConditon.getOperator() == ConditionUnitOperator.EQUAL){
                    // values
                    executeRule(targetList, rule.getFunction(), column, matchingConditon.getValues().get(0));
                }else if (matchingConditon.getOperator() == ConditionUnitOperator.IN){
                    for (Object obj : matchingConditon.getValues()){
                        executeRule(targetList, rule.getFunction(), column, obj);
                    }
                }
            }
            // 以第一个有结果的PartitionColumn为准,因为Baymax3.0规定，对于有多个分区列的，路由结果必须相同.
            if (targetList.size() != 0){
                break;
            }
        }
        return targetList;
    }

    private void executeRule(List<TargetTableEntity> targetList, PartitionFunction rule, PartitionColumn column, Object value){
        if (column.getProcess() != null){
            value = column.getProcess().apply(value);
        }
        TargetTableEntity target = executeRule(rule, column.getName(), value);
        if (target != null && target.getTargetDB() == null && target.getTargetTable() != null){
            throw new BayMaxException(target.getTargetTable() + "没有对应的库");
        }
        if (target != null && target.getTargetDB() != null && target.getTargetTable() != null){
            targetList.add(target);
        }
    }

    private TargetTableEntity executeRule(PartitionFunction rule, String column, Object value) {

        String targetDB = null;
        String targetTable = null;

        Object ruleResult = rule.execute(String.valueOf(value), null);
        if (Integer.class == ruleResult.getClass() || Integer.class.isAssignableFrom(ruleResult.getClass())) {
            // Integer
            String suffix = super.getSuffix((Integer) ruleResult);
            targetTable = super.format(suffix);
            targetDB = super.nodeMapping.getMapping().get(suffix);
        } else {
            throw new BayMaxException("is the express can return integer only!" + rule);
        }
        return new TargetTableEntity(targetDB, targetTable);
    }

    /**
     * 返回所有分区-表的映射 格式: p1-order001 p1-order002 p2-order003 p3-order004
     *
     * @return
     */
    public List<TargetTableEntity> getAllTableNames() {
        List<TargetTableEntity> allTables = new ArrayList<TargetTableEntity>();
        Iterator<Map.Entry<String, String>> ite = super.nodeMapping.getMapping().entrySet().iterator();
        while(ite.hasNext()){
            Map.Entry<String, String> entry = ite.next();
            allTables.add(new TargetTableEntity(entry.getValue(), super.format(entry.getKey())));
        }
        return allTables;
    }

    @Override
    public String toString() {
        return super.toString();
    }
}

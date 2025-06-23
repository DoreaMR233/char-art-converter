import threading
from typing import Dict, TypeVar, Generic, List, Any

# 定义类型变量
K = TypeVar('K')
V = TypeVar('V')

class ThreadSafeDict(Generic[K, V]):
    """线程安全的字典实现。
    
    使用RLock（可重入锁）确保在多线程环境下对字典的操作是线程安全的。
    支持标准字典的所有基本操作，包括get、set、delete、contains等。
    """
    
    def __init__(self):
        """初始化线程安全字典。
        
        创建内部字典和可重入锁实例。
        """
        self._dict = {}
        self._lock = threading.RLock()
    
    def get(self, key, default=None):
        """获取指定键的值。
        
        Args:
            key: 要获取的键
            default: 键不存在时返回的默认值
            
        Returns:
            键对应的值，如果键不存在则返回默认值
        """
        with self._lock:
            return self._dict.get(key, default)
    
    def __getitem__(self, key):
        """获取值（使用[]操作符）。
        
        Args:
            key: 要获取的键
            
        Returns:
            键对应的值
            
        Raises:
            KeyError: 当键不存在时抛出
        """
        with self._lock:
            return self._dict[key]
    
    def __setitem__(self, key, value):
        """设置值（使用[]操作符）。
        
        Args:
            key: 要设置的键
            value: 要设置的值
        """
        with self._lock:
            self._dict[key] = value
    
    def __delitem__(self, key):
        """删除键值对（使用del操作符）。
        
        Args:
            key: 要删除的键
            
        Raises:
            KeyError: 当键不存在时抛出
        """
        with self._lock:
            del self._dict[key]
    
    def __contains__(self, key):
        """检查键是否存在（使用in操作符）。
        
        Args:
            key: 要检查的键
            
        Returns:
            bool: 键存在返回True，否则返回False
        """
        with self._lock:
            return key in self._dict
    
    def copy(self):
        """返回字典的副本。
        
        Returns:
            dict: 内部字典的浅拷贝
        """
        with self._lock:
            return self._dict.copy()
    
    def items(self):
        """返回所有键值对的列表。
        
        Returns:
            list: 包含所有键值对元组的列表
        """
        with self._lock:
            return list(self._dict.items())
    
    def keys(self):
        """返回所有键的列表。
        
        Returns:
            list: 包含所有键的列表
        """
        with self._lock:
            return list(self._dict.keys())
    
    def values(self):
        """返回所有值的列表。
        
        Returns:
            list: 包含所有值的列表
        """
        with self._lock:
            return list(self._dict.values())
    
    def __str__(self):
        """返回字典的字符串表示。
        
        Returns:
            str: 字典的字符串表示
        """
        with self._lock:
            return str(self._dict)
    
    def __repr__(self):
        """返回对象的字符串表示。
        
        Returns:
            str: 对象的详细字符串表示
        """
        with self._lock:
            return f"ThreadSafeDict({self._dict})"
    
    def get_last_item(self):
        """获取最后一个添加的键值对。
        
        Returns:
            tuple or None: 最后一个键值对的元组(key, value)，如果字典为空则返回None
        """
        with self._lock:
            if not self._dict:
                return None
            # 获取最后一个键值对
            last_key = list(self._dict.keys())[-1]
            return last_key, self._dict[last_key]